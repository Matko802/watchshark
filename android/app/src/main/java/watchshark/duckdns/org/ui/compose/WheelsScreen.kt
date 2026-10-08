package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.PlayerCache
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.fmtNum
import watchshark.duckdns.org.ui.httpErrorMessage

private fun parseWheelVideo(o: JsonObject): Video? {
    return try {
        val id = o.get("id")?.asLong ?: 0
        if (id == 0L) return null
        Video(
            id = id,
            title = o.get("title")?.asString ?: "",
            username = o.get("username")?.asString ?: "",
            userId = o.get("user_id")?.asLong ?: 0,
            src = o.get("src")?.asString ?: "",
            thumbnail = o.get("thumbnail")?.asString,
            views = o.get("views")?.asLong ?: 0,
            likes = o.get("likes")?.asLong ?: 0,
            liked = o.get("liked")?.asBoolean == true,
            followers = o.get("followers")?.asLong ?: 0,
            following = o.get("following")?.asBoolean == true,
            created_at = o.get("created_at")?.asString ?: "",
            avatar = o.get("avatar")?.asString,
            kind = o.get("kind")?.asString ?: "wheel",
            renditions = try {
                o.getAsJsonObject("renditions")?.entrySet()
                    ?.associate { e -> e.key to e.value.asString }
            } catch (_: Exception) {
                null
            },
            orientation = try {
                o.get("orientation")?.asString
            } catch (_: Exception) {
                null
            },
        )
    } catch (_: Exception) {
        null
    }
}

/** Dynamic rendition URL (generates on first request server-side). */
private fun dynRendition(src: String, res: String): String? {
    val stem = Regex("""/v/(.+)\.[a-z0-9]+$""", RegexOption.IGNORE_CASE)
        .find(src)?.groupValues?.get(1)
        ?.removeSuffix("-720p")?.removeSuffix("-480p")?.removeSuffix("-360p")
        ?: return null
    return "/v/$stem-$res.webm"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelsScreen(
    onOpenVideo: (Long) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val videos = remember { mutableStateListOf<Video>() }
    val seen = remember { mutableSetOf<Long>() }
    val qualityOverride = remember { mutableStateMapOf<Long, String>() }
    val autoKeys = remember { mutableMapOf<Long, String>() }
    val readyMap = remember { mutableStateMapOf<Int, Boolean>() }
    var loading by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qualityFor by remember { mutableStateOf<Video?>(null) }

    fun fullUrl(path: String?): String? = ApiClient.fullUrl(path)

    fun cachedSource(url: String): androidx.media3.exoplayer.source.MediaSource {
        val props = ApiClient.authCookie()?.let { mapOf("Cookie" to it) } ?: emptyMap()
        return PlayerCache.mediaSource(appCtx, url, props)
    }

    fun srcFor(v: Video): String? {
        val override = qualityOverride[v.id]
        val url = when {
            override != null -> v.renditions?.get(override)
                ?: override.let { dynRendition(v.src, it) } ?: v.src
            else -> {
                val key = AutoQuality.pickReadyKey(v)
                autoKeys[v.id] = key
                AutoQuality.readyUrl(v, key)
                    ?: v.renditions?.get("720p")
                    ?: v.renditions?.get("480p")
                    ?: v.renditions?.get("360p")
                    ?: v.src
            }
        }
        return fullUrl(url)
    }

    val player = remember {
        ApiClient.buildPlayer(appCtx).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    readyMap.remove(currentMediaItemIndex)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    val idx = currentMediaItemIndex
                    if (state == Player.STATE_READY) {
                        readyMap[idx] = true
                    } else if (state == Player.STATE_BUFFERING && playWhenReady &&
                        readyMap[idx] == true
                    ) {
                        // Adaptive step-down while rebuffering.
                        val vid = videos.getOrNull(idx) ?: return
                        if (qualityOverride.containsKey(vid.id)) return
                        val want = AutoQuality.lowerReadyKey(vid, autoKeys[vid.id]) ?: return
                        val url = AutoQuality.readyUrl(vid, want) ?: return
                        if (!AutoQuality.tryBeginSwitch(AutoQuality.DOWNGRADE_GAP_MS)) return
                        autoKeys[vid.id] = want
                        readyMap.remove(idx)
                        val time = currentPosition.coerceAtLeast(0)
                        val playing = isPlaying
                        removeMediaItem(idx)
                        addMediaSource(idx, cachedSource(url))
                        seekTo(idx, time)
                        if (playing) play()
                    }
                }
            })
        }
    }

    // Pause with the app, resume when back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            player.release()
        }
    }

    fun loadMore() {
        if (loading || exhausted) return
        loading = true
        error = null
        scope.launch {
            try {
                var added = 0
                repeat(5) {
                    if (exhausted) return@repeat
                    try {
                        val q = if (seen.isEmpty()) null
                        else seen.takeLast(128).joinToString(",")
                        val res = ApiClient.api.wheels(q)
                        val vid = res.getAsJsonObject("video")?.let { parseWheelVideo(it) }
                        if (vid == null || seen.contains(vid.id)) return@repeat
                        seen.add(vid.id)
                        val url = srcFor(vid) ?: return@repeat
                        videos.add(vid)
                        player.addMediaSource(cachedSource(url))
                        added++
                    } catch (_: Exception) {
                    }
                }
                if (added == 0) {
                    exhausted = true
                    if (videos.isEmpty()) error = "No wheels yet"
                } else if (!prepared) {
                    player.prepare()
                    player.seekTo(0, 0)
                    prepared = true
                    player.playWhenReady = true
                }
            } finally {
                loading = false
            }
        }
    }

    fun autoUpgradeCurrent(position: Int) {
        val vid = videos.getOrNull(position) ?: return
        if (qualityOverride.containsKey(vid.id)) return
        if (position >= player.mediaItemCount) return
        val want = AutoQuality.pickReadyKey(vid)
        if (AutoQuality.rungIndex(want) <= AutoQuality.rungIndex(autoKeys[vid.id])) return
        val url = AutoQuality.readyUrl(vid, want) ?: return
        if (!AutoQuality.tryBeginSwitch(AutoQuality.UPGRADE_GAP_MS)) return
        autoKeys[vid.id] = want
        val wasIndex = player.currentMediaItemIndex
        val time = player.currentPosition.coerceAtLeast(0)
        val playing = player.isPlaying
        player.removeMediaItem(position)
        player.addMediaSource(position, cachedSource(url))
        if (wasIndex == position) {
            player.seekTo(position, time)
            if (playing) player.play()
        }
    }

    fun watchAgain() {
        seen.clear()
        videos.clear()
        autoKeys.clear()
        readyMap.clear()
        exhausted = false
        prepared = false
        player.stop()
        player.clearMediaItems()
        loadMore()
    }

    LaunchedEffect(Unit) { loadMore() }

    val pageCount = videos.size + if (exhausted && videos.isNotEmpty()) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val currentPage = pagerState.currentPage

    LaunchedEffect(currentPage) {
        if (currentPage < videos.size && currentPage < player.mediaItemCount) {
            if (player.currentMediaItemIndex != currentPage) {
                player.seekTo(currentPage, 0)
            }
            player.playWhenReady = true
            autoUpgradeCurrent(currentPage)
        }
        if (currentPage >= videos.size - 3) loadMore()
    }

    if (videos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else Text(error ?: "No wheels yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        if (page >= videos.size) {
            // End card.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You're all caught up", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            watchAgain()
                            scope.launch { pagerState.scrollToPage(0) }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Watch again") }
                }
            }
            return@VerticalPager
        }
        val vid = videos[page]
        val isCurrent = page == currentPage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (player.isPlaying) player.pause() else player.play()
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    view.player = if (isCurrent) player else null
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (readyMap[page] != true) {
                AsyncImage(
                    model = fullUrl(vid.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Bottom scrim so captions stay readable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(top = 96.dp),
            )
            // Captions above the floating pill.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.78f)
                    .padding(start = 12.dp, bottom = 96.dp, end = 8.dp),
            ) {
                Text(
                    vid.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                )
                Text(
                    "@${vid.username} • ${fmtNum(vid.views)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDDDDDD),
                    modifier = Modifier.clickable { onOpenChannel(vid.username) },
                )
            }
            // Action rail.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = {
                    scope.launch {
                        try {
                            val res = ApiClient.api.like(vid.id)
                            val idx = videos.indexOfFirst { it.id == vid.id }
                            if (idx >= 0) {
                                videos[idx] = vid.copy(
                                    liked = res.get("liked")?.asBoolean == true,
                                    likes = res.get("likes")?.asLong ?: vid.likes,
                                )
                            }
                        } catch (e: Exception) {
                            error = httpErrorMessage(e)
                        }
                    }
                }) {
                    Icon(
                        if (vid.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (vid.liked) Color(0xFFFF5C5C) else Color.White,
                    )
                }
                Text(fmtNum(vid.likes), color = Color.White, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                }) {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = "Mute",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { onOpenVideo(vid.id) }) {
                    Icon(Icons.Filled.Comment, contentDescription = "Comments", tint = Color.White)
                }
                TextButton(onClick = { qualityFor = vid }) {
                    Text(
                        qualityOverride[vid.id] ?: "Auto",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    // Quality picker.
    qualityFor?.let { v ->
        val options = buildList {
            add("Auto")
            if (v.renditions?.containsKey("720p") == true || dynRendition(v.src, "720p") != null) add("720p")
            if (v.renditions?.containsKey("480p") == true || dynRendition(v.src, "480p") != null) add("480p")
            if (v.renditions?.containsKey("360p") == true || dynRendition(v.src, "360p") != null) add("360p")
            add("Source")
        }
        DropdownMenu(expanded = true, onDismissRequest = { qualityFor = null }) {
            options.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        qualityFor = null
                        val key = when {
                            label.startsWith("720p") -> "720p"
                            label.startsWith("480p") -> "480p"
                            label.startsWith("360p") -> "360p"
                            else -> null
                        }
                        if (key == null) qualityOverride.remove(v.id)
                        else qualityOverride[v.id] = key
                        val pos = videos.indexOfFirst { it.id == v.id }
                        if (pos < 0) return@DropdownMenuItem
                        val url = fullUrl(srcFor(v) ?: v.src) ?: return@DropdownMenuItem
                        val wasIndex = player.currentMediaItemIndex
                        val time = player.currentPosition
                        val playing = player.isPlaying
                        player.removeMediaItem(pos)
                        player.addMediaSource(pos, cachedSource(url))
                        if (wasIndex == pos) {
                            player.seekTo(pos, time)
                            if (playing) player.play()
                        }
                    },
                )
            }
        }
    }

    if (loading && videos.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
    }
}
