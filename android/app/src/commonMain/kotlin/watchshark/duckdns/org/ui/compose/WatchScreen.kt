package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import watchshark.duckdns.org.ui.PlatformLocal
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import watchshark.duckdns.org.*
import watchshark.duckdns.org.Res
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AppErrors
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.data.PlayerManager
import watchshark.duckdns.org.data.PPlayer
import watchshark.duckdns.org.data.QualityKit
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.Uikit
import watchshark.duckdns.org.ui.compose.common.ReplyTargetChip
import watchshark.duckdns.org.ui.compose.common.Shimmer
import watchshark.duckdns.org.ui.compose.common.threadedComments
import watchshark.duckdns.org.ui.compose.player.WatchPlayer
import watchshark.duckdns.org.ui.compose.player.VideoPlaying
import watchshark.duckdns.org.ui.fmtAge
import watchshark.duckdns.org.ui.fmtNum
import watchshark.duckdns.org.ui.httpErrorMessage
import watchshark.duckdns.org.ui.toast
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    videoId: Long,
    player: PPlayer,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val context = PlatformLocal.app
    val scope = rememberCoroutineScope()
    var video by remember { mutableStateOf<Video?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var meId by remember { mutableStateOf<Long?>(null) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
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

    fun toggleLike() {
        val cur = video ?: return
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
                context.toast(httpErrorMessage(e))
                video = cur
            }
        }
    }

    fun likeIfNotLiked() {
        if (video?.liked == false) toggleLike()
    }

    val doubleTapLike = remember { { likeIfNotLiked() } }


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

    val playbackUrl = if (video?.status == "ready") video?.let { QualityKit.url(it, quality) } else null
    var preparedUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playbackUrl) {
        val url = playbackUrl ?: return@LaunchedEffect
        if (preparedUrl == url) return@LaunchedEffect
        try {
            player.switchTo(url)
            preparedUrl = url
        } catch (_: Exception) {
        }
    }

    if (loading && video == null) {
        Column(modifier = modifier.fillMaxSize()) {
            Shimmer(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                RoundedCornerShape(0.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Shimmer(Modifier.fillMaxWidth(0.72f).height(22.dp))
                Shimmer(Modifier.fillMaxWidth(0.42f).height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Shimmer(Modifier.size(36.dp), CircleShape)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Shimmer(Modifier.fillMaxWidth(0.35f).height(14.dp))
                        Shimmer(Modifier.fillMaxWidth(0.25f).height(12.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Shimmer(Modifier.width(120.dp).height(36.dp), RoundedCornerShape(50))
                    Shimmer(Modifier.width(90.dp).height(36.dp), RoundedCornerShape(50))
                }
            }
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

    if (v.status == "processing") {
        LaunchedEffect(v.id) {
            while (isActive) {
                delay(4000)
                try {
                    val res = ApiClient.api.videoDetail(videoId)
                    val nv = res.video
                    if (nv != null) {
                        video = nv
                        if (nv.status != "processing") break
                    }
                } catch (_: Exception) {
                }
            }
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(v.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Processing video…",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val pct = (v.progress / 100.0).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { if (pct > 0.02f) pct else 0.02f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                when (v.stage) {
                    "compressing" -> "Compressing ${v.progress.roundToInt()}% — keeping it under 80 MB"
                    "finalizing" -> "Finishing up…"
                    else -> "Checking video…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "This page updates automatically. Only you can see it here until it is ready.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (v.status == "failed") {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(v.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Processing failed. This file could not be converted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    onDoubleTapLike = doubleTapLike,
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
                var headerExpanded by remember(v.id) { mutableStateOf(false) }
                val chevronDeg by animateFloatAsState(
                    targetValue = if (headerExpanded) 180f else 0f,
                    label = "headerChevron",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    Haptics.tick(context)
                                    headerExpanded = !headerExpanded
                                },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        AsyncImage(
                            model = ApiClient.fullUrl(v.avatar),
                            contentDescription = v.username,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "@${v.username}",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${fmtNum(v.followers)} followers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            painterResource(Res.drawable.ic_expand_more),
                            contentDescription = if (headerExpanded) "Collapse channel info" else "Expand channel info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = chevronDeg },
                        )
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
                                        if (v.following) Res.drawable.ic_check
                                        else Res.drawable.ic_person_add,
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
                }
                AnimatedVisibility(
                    visible = headerExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    label = "headerBio",
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${fmtNum(v.views)} views • ${fmtAge(v.created_at)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            v.description?.takeIf { it.isNotBlank() } ?: "No description yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onOpenChannel(v.username) }) {
                            Text("Visit channel")
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (v.liked) Color(0xFFFF5C5C).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { toggleLike() },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                val likeBounce = remember(v.id) { Animatable(1f) }
                LaunchedEffect(v.liked) {
                    if (v.liked) {
                        likeBounce.snapTo(0.5f)
                        likeBounce.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
                Icon(
                                    painterResource(
                                        if (v.liked) Res.drawable.ic_favorite_fill
                                        else Res.drawable.ic_favorite_outline,
                                    ),
                                    contentDescription = if (v.liked) "Unlike" else "Like",
                                    tint = if (v.liked) Color(0xFFFF5C5C)
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            scaleX = likeBounce.value
                                            scaleY = likeBounce.value
                                        },
                                )
                                Text(
                                    "${fmtNum(v.likes)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (v.liked) Color(0xFFFF5C5C)
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape,
                                    ),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            Haptics.tick(context)
                                            try {
                                                val url = ApiClient.BASE_URL.trimEnd('/') + "/watch/" + v.id
                                                Uikit.shareText("${v.title} $url", "Share")
                                            } catch (e: Exception) {
                                                AppErrors.log(e, "share")
                                            }
                                        },
                                    ),
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_open_in_new),
                                    contentDescription = "Share ${v.title}",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
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
                        onValueChange = {
                            draft = it
                            commentError = null
                        },
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
                        commentError = null
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
                                commentError = httpErrorMessage(e)
                            } finally {
                                posting = false
                            }
                        }
                    }) {
                        Icon(
                            painterResource(Res.drawable.ic_send),
                            contentDescription = "Post comment"
                        )
                    }
                }
                commentError?.let { msg ->
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
