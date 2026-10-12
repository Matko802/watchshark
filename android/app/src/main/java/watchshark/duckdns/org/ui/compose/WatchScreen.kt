package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AppErrors
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.PlayerManager
import watchshark.duckdns.org.data.QualityKit
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.compose.common.ReplyTargetChip
import watchshark.duckdns.org.ui.compose.common.threadedComments
import watchshark.duckdns.org.ui.compose.player.WatchPlayer
import watchshark.duckdns.org.ui.compose.player.VideoPlaying
import watchshark.duckdns.org.ui.fmtAge
import watchshark.duckdns.org.ui.fmtNum
import watchshark.duckdns.org.ui.httpErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    videoId: Long,
    player: ExoPlayer,
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
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var replyTo by remember(videoId) { mutableStateOf<Comment?>(null) }
    val expandedThreads = remember { mutableStateMapOf<Long, Boolean>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                PlayerManager.pauseForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    suspend fun reloadComments() {
        try {
            comments = ApiClient.api.videoDetail(videoId).comments.orEmpty()
        } catch (e: Exception) {
            AppErrors.log(e, "watchComments")
        }
    }

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


    DisposableEffect(videoId) {
        onDispose {
            try {
                player.stop()
            } catch (_: Exception) {
            }
            VideoPlaying.setPlaying(false)
        }
    }
    var quality by remember(videoId) { mutableStateOf("Auto") }
    val playback = watchshark.duckdns.org.ui.compose.player.rememberPlayerUiState(player)
    LaunchedEffect(playback.isPlaying) {
        VideoPlaying.setPlaying(playback.isPlaying)
    }

    fun qualityOptions(v: Video): List<String> = QualityKit.options(v)

    fun qualityUrl(v: Video, selected: String): String? = QualityKit.url(v, selected)

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

    LazyColumn(modifier = modifier.fillMaxSize().imePadding()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
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
                        Surface(
                            shape = CircleShape,
                            color = if (v.following) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .height(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val cur = video ?: return@clickable
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
                                ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    painterResource(
                                        if (v.following) R.drawable.ic_check
                                        else R.drawable.ic_person_add,
                                    ),
                                    contentDescription = if (v.following) "Following ${v.username}" else "Follow ${v.username}",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    if (v.following) "Following" else "Follow",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                val cur = video ?: return@clickable
                                Haptics.tick(context)
                                video = cur.copy(
                                    liked = !cur.liked,
                                    likes = (cur.likes + if (cur.liked) -1 else 1).coerceAtLeast(0),
                                )
                                scope.launch {
                                    try {
                                        ApiClient.api.like(cur.id)
                                    } catch (e: Exception) {
                                        AppErrors.log(e, "like")
                                        video = cur
                                    }
                                }
                            },
                        ),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (v.liked) Color(0xFFFF5C5C).copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(
                                        if (v.liked) R.drawable.ic_favorite_fill
                                        else R.drawable.ic_favorite_outline,
                                    ),
                                    contentDescription = if (v.liked) "Unlike" else "Like",
                                    tint = if (v.liked) Color(0xFFFF5C5C)
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Text(
                            "${fmtNum(v.likes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        Haptics.tick(context)
                        try {
                            val url = ApiClient.BASE_URL.trimEnd('/') + "/watch/" + v.id
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${v.title} $url")
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Share"))
                        } catch (e: Exception) {
                            AppErrors.log(e, "share")
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_open_in_new),
                            contentDescription = "Share ${v.title}"
                        )
                    }
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
                replyTo?.let { target ->
                    ReplyTargetChip(
                        username = target.username,
                        onClear = { replyTo = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = {
                            Text(
                                replyTo?.let { "Reply to @${it.username}…" }
                                    ?: "Add a comment…"
                            )
                        },
                        singleLine = false,
                        maxLines = 3,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val body = draft.trim()
                        if (body.isEmpty() || posting) return@IconButton
                        posting = true
                        scope.launch {
                            try {
                                val payload = buildMap<String, Any> {
                                    put("body", body)
                                    replyTo?.id?.let { put("parent_id", it) }
                                }
                                ApiClient.api.comment(v.id, payload)
                                draft = ""
                                replyTo = null
                                reloadComments()
                            } catch (e: Exception) {
                                AppErrors.log(e, "comment")
                                error = httpErrorMessage(e)
                            } finally {
                                posting = false
                            }
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_send),
                            contentDescription = "Post comment"
                        )
                    }
                }
            }
        }
        threadedComments(
            comments = comments,
            expanded = expandedThreads,
            onReply = { replyTo = it }
        )

        item { Spacer(modifier = Modifier.height(104.dp)) }
    }
}
