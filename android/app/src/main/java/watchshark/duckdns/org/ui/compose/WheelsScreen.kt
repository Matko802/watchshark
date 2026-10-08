package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.PlayerCache
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.compose.theme.AppMotion
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
                        else seen.toList().takeLast(128).joinToString(",")
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


    if (videos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else Text(error ?: "No wheels yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

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
        // Pager depth transformer: neighbors shrink + fade for a TikTok-like feel.
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val offsetAbs = pageOffset.coerceIn(-1f, 1f).let { kotlin.math.abs(it) }
        var heartBurst by remember(vid.id) { mutableStateOf(false) }
        fun doLike() {
            // Optimistic: flip the heart instantly so the tap never
            // feels dead on slow networks; revert only on failure.
            val idx = videos.indexOfFirst { it.id == vid.id }
            if (idx < 0) return
            val cur = videos[idx]
            videos[idx] = cur.copy(
                liked = !cur.liked,
                likes = (cur.likes + if (cur.liked) -1 else 1).coerceAtLeast(0),
            )
            scope.launch {
                try {
                    ApiClient.api.like(vid.id)
                } catch (e: Exception) {
                    val i = videos.indexOfFirst { it.id == vid.id }
                    if (i >= 0) videos[i] = cur
                    error = httpErrorMessage(e)
                }
            }
        }
        LaunchedEffect(heartBurst) {
            if (heartBurst) {
                delay(800)
                heartBurst = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = (1f - 0.12f * offsetAbs).coerceIn(0.88f, 1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - 0.35f * offsetAbs).coerceIn(0.65f, 1f)
                }
                .background(Color.Black)
                .pointerInput(vid.id) {
                    detectTapGestures(
                        onTap = { if (player.isPlaying) player.pause() else player.play() },
                        onDoubleTap = {
                            heartBurst = true
                            if (!vid.liked) doLike()
                        },
                    )
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
            // Double-tap heart burst.
            AnimatedVisibility(
                visible = heartBurst,
                enter = scaleIn(AppMotion.fastSpatial, initialScale = 0.4f) + fadeIn(),
                exit = scaleOut(targetScale = 1.4f) + fadeOut(),
                modifier = Modifier.align(Alignment.Center),
                label = "heartBurst",
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF5C5C),
                    modifier = Modifier.size(96.dp),
                )
            }
            // Captions above the floating pill.
            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 4 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
                label = "captions",
            ) {
            Column(
                modifier = Modifier
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
            }
            // Action rail.
            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 4 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd),
                label = "actions",
            ) {
            Column(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Heart like, 1:1 with web (favorite outline/filled, always white).
                RailPillButton(onClick = { doLike() }, description = "Like") {
                    Icon(
                        painterResource(
                            if (vid.liked) R.drawable.ic_favorite_fill
                            else R.drawable.ic_favorite_outline,
                        ),
                        contentDescription = "Like",
                    )
                }
                Text(fmtNum(vid.likes), color = Color.White, style = MaterialTheme.typography.labelSmall)
                RailPillButton(
                    onClick = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    },
                    description = "Mute",
                ) {
                    Icon(
                        painterResource(
                            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
                        ),
                        contentDescription = "Mute",
                    )
                }
                RailPillButton(onClick = { onOpenVideo(vid.id) }, description = "Comments") {
                    Icon(
                        painterResource(R.drawable.ic_chat),
                        contentDescription = "Comments",
                    )
                }
                // Quality gear + label, like web's settings rail button.
                RailPillButton(onClick = { qualityFor = vid }, description = "Quality") {
                    Icon(
                        painterResource(R.drawable.ic_settings),
                        contentDescription = "Quality",
                    )
                }
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

/**
 * Over-video circular pill button, 1:1 with web
 * (.reel-rail .railitem md-icon-button): translucent black circle,
 * white icon, darker background + 0.92 squeeze on press.
 */
@Composable
private fun RailPillButton(
    onClick: () -> Unit,
    description: String,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 0.55f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "railBg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "railPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            icon()
        }
    }
}
