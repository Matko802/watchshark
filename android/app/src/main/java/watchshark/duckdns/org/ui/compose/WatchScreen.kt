package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.compose.player.WatchPlayer
import watchshark.duckdns.org.ui.compose.player.VideoPlaying
import watchshark.duckdns.org.ui.compose.player.dynRenditionUrl
import watchshark.duckdns.org.ui.compose.player.rememberPlayerUiState
import watchshark.duckdns.org.ui.fmtAge
import watchshark.duckdns.org.ui.fmtNum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    videoId: Long,
    player: ExoPlayer,
    miniVideo: Video?,
    onMinimize: (Video) -> Unit,
    onExpand: () -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var video by remember { mutableStateOf<Video?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var meId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(videoId) {
        loading = true
        error = null
        try {
            meId = try {
                ApiClient.api.me().user?.id
            } catch (_: Exception) {
                null
            }
            val res = ApiClient.api.videoDetail(videoId)
            video = res.video
            comments = res.comments.orEmpty()
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        } finally {
            loading = false
        }
    }


    val miniNow = rememberUpdatedState(miniVideo)
    DisposableEffect(videoId) {
        onDispose {
            if (miniNow.value?.id != videoId) {
                try {
                    player.stop()
                } catch (_: Exception) {
                }
            }
            VideoPlaying.setPlaying(false)
        }
    }
    LaunchedEffect(videoId, miniVideo?.id) {
        if (miniVideo?.id != null && miniVideo?.id != videoId) onExpand()
    }
    var quality by remember(videoId) { mutableStateOf("Auto") }
    val playback = rememberPlayerUiState(player)
    LaunchedEffect(playback.isPlaying) {
        VideoPlaying.setPlaying(playback.isPlaying)
    }

    fun qualityOptions(v: Video): List<String> {
        return buildList {
            add("Auto")
            if (v.renditions?.containsKey("720p") == true || dynRenditionUrl(v.src, "720p") != null) add("720p")
            if (v.renditions?.containsKey("480p") == true || dynRenditionUrl(v.src, "480p") != null) add("480p")
            if (v.renditions?.containsKey("360p") == true || dynRenditionUrl(v.src, "360p") != null) add("360p")
            add("Source")
        }
    }

    fun qualityUrl(v: Video, selected: String): String? {
        if (selected != "Auto" && selected != "Source") {
            return v.renditions?.get(selected)
                ?: dynRenditionUrl(v.src, selected)
                ?: ApiClient.fullUrl(v.src)
        }
        if (selected == "Auto") {
            val key = AutoQuality.pickReadyKey(v)
            AutoQuality.readyUrl(v, key)?.let { return it }
        }
        return v.renditions?.get("720p")
            ?: v.renditions?.values?.firstOrNull()
            ?: ApiClient.fullUrl(v.src)
    }

    LaunchedEffect(video, quality) {
        val v = video ?: return@LaunchedEffect
        val url = qualityUrl(v, quality) ?: return@LaunchedEffect
        try {
            val keep = player.playbackState != Player.STATE_IDLE && player.duration.coerceAtLeast(0) > 0
            val pos = if (keep) player.currentPosition.coerceAtLeast(0) else 0L
            player.setMediaItem(ApiClient.mediaItem(url))
            player.prepare()
            player.seekTo(pos)
            player.playWhenReady = true
        } catch (_: Exception) {
        }
    }

    if (loading && video == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val v = video
    if (v == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error ?: "Video not found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val density = LocalDensity.current
    val miniThreshold = with(density) { 110.dp.toPx() }
    var dragY by remember(videoId) { mutableFloatStateOf(0f) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        translationY = dragY
                        val shrink = (1f - dragY / 1600f).coerceIn(0.85f, 1f)
                        scaleX = shrink
                        scaleY = shrink
                    }
                    .pointerInput(videoId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragY = 0f },
                            onDrag = { _, dragAmount ->
                                dragY = (dragY + dragAmount.y).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (dragY > miniThreshold) onMinimize(v)
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        )
                    },
            ) {
                WatchPlayer(
                    player = player,
                    thumbnailUrl = ApiClient.fullUrl(v.thumbnail),
                    qualities = qualityOptions(v),
                    quality = quality,
                    onQuality = { quality = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(v.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${fmtNum(v.views)} views • ${fmtAge(v.created_at)} • ${fmtNum(v.likes)} likes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ApiClient.fullUrl(v.avatar),
                        contentDescription = v.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                    TextButton(onClick = { onOpenChannel(v.username) }) {
                        Text("@${v.username}")
                    }


                    if (meId != null && meId != v.userId) {
                        OutlinedButton(
                            onClick = {
                                val cur = video ?: return@OutlinedButton
                                Haptics.tick(context)
                                video = cur.copy(
                                    following = !cur.following,
                                    followers = (cur.followers + if (cur.following) -1 else 1).coerceAtLeast(0),
                                )
                                scope.launch {
                                    try {
                                        ApiClient.api.follow(cur.userId)
                                    } catch (_: Exception) {
                                        video = cur
                                    }
                                }
                            },
                        ) {
                            if (!v.following) {
                                Icon(
                                    painterResource(R.drawable.ic_person_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(if (v.following) "Following" else "Follow")
                        }
                    }
                    FilterChip(
                        selected = v.liked,
                        onClick = {
                            val cur = video ?: return@FilterChip
                            Haptics.tick(context)
                            video = cur.copy(
                                liked = !cur.liked,
                                likes = (cur.likes + if (cur.liked) -1 else 1).coerceAtLeast(0),
                            )
                            scope.launch {
                                try {
                                    ApiClient.api.like(cur.id)
                                } catch (_: Exception) {
                                    video = cur
                                }
                            }
                        },
                        label = { Text("${fmtNum(v.likes)}") },
                        leadingIcon = {
                            Icon(
                                painterResource(
                                    if (v.liked) R.drawable.ic_favorite_fill
                                    else R.drawable.ic_favorite_outline,
                                ),
                                contentDescription = null,
                            )
                        },
                    )
                }
                if (!v.description.isNullOrEmpty()) {
                    var descExpanded by remember(v.id) { mutableStateOf(false) }
                    Text(
                        v.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { descExpanded = !descExpanded }
                            .animateContentSize()
                            .padding(top = 4.dp),
                    )
                }
                Text(
                    "Comments (${comments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(comments, key = { it.id }) { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .animateItem(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (c.avatar != null) {
                    AsyncImage(
                        model = ApiClient.fullUrl(c.avatar),
                        contentDescription = c.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_person),
                        contentDescription = null,
                    )
                }
                Column {
                    Text(
                        "@${c.username} • ${fmtAge(c.created_at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }
}

@Composable
fun MiniPlayerBar(
    video: Video,
    playing: Boolean,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            AsyncImage(
                model = ApiClient.fullUrl(video.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand,
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExpand,
                    ),
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${video.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    ),
            ) {
                Icon(
                    painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
