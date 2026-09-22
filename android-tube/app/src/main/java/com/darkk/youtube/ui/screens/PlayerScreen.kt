package com.darkk.youtube.ui.screens

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.darkk.youtube.innertube.PlayerData
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.innertube.VideoQuality
import com.darkk.youtube.viewmodel.PlayerUiState
import com.darkk.youtube.viewmodel.YouTubeViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// PlayerScreen  – shared ExoPlayer for mini + full, no blank screen on switch
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    videoTitle: String,
    viewModel: YouTubeViewModel,
    repository: com.darkk.youtube.data.LocalRepository,
    isMini: Boolean = false,
    bottomNavHeight: Dp = 0.dp,
    onBack: () -> Unit,
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    onRelatedVideoClick: (com.darkk.youtube.innertube.VideoItem) -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val context = LocalContext.current

    // ── Shared ExoPlayer – created ONCE, survives mini↔full transitions ──────
    var exoPlayer by remember {
        mutableStateOf(
            run {
                val player = com.darkk.youtube.playback.PlaybackService.getSharedPlayer(context).apply {
                    playWhenReady = true
                }
                val intent = android.content.Intent(context, com.darkk.youtube.playback.PlaybackService::class.java)
                context.startService(intent)
                com.darkk.youtube.playback.PlaybackService.swapPlayer(player)
                player
            }
        )
    }

    // Load player data whenever videoId changes
    LaunchedEffect(videoId) {
        exoPlayer.clearMediaItems()
        viewModel.loadPlayerData(videoId)
    }

    LaunchedEffect(playerState) {
        if (playerState is com.darkk.youtube.viewmodel.PlayerUiState.Ready) {
            val data = (playerState as com.darkk.youtube.viewmodel.PlayerUiState.Ready).data
            val video = com.darkk.youtube.innertube.VideoItem(
                videoId = data.videoId,
                title = data.title,
                thumbnail = data.thumbnail,
                channelName = data.author,
                channelAvatar = data.channelAvatar,
                viewCount = data.views,
                publishedAt = data.uploadDate,
                duration = ""
            )
            repository.addToHistory(video)
        }
    }
    
    var isLiked by remember { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        isLiked = repository.isLiked(videoId)
    }
    LaunchedEffect(playerState) {
        val ready = playerState as? com.darkk.youtube.viewmodel.PlayerUiState.Ready
        if (ready != null && ready.data.userLiked) isLiked = true
    }
    
    val globalPlaylists by repository.playlists.collectAsState()
    val isSaved = globalPlaylists.any { it.id != "liked_videos" && it.videos.any { v -> v.videoId == videoId } }
    
    var showSaveSheet by remember { mutableStateOf(false) }

    // Media initialization is handled entirely by VideoPlayerPane's selectedQuality effect

    // Release player only when the composable leaves composition completely
    val currentExoPlayer by rememberUpdatedState(exoPlayer)
    DisposableEffect(Unit) {
        onDispose {
            currentExoPlayer.release()
            viewModel.resetPlayer()
        }
    }

    // ── Drag Animation State ──────────────────────────────────────────────────
    val coroutineScope = rememberCoroutineScope()
    val fractionAnim = remember { Animatable(if (isMini) 1f else 0f) }
    
    LaunchedEffect(isMini) {
        fractionAnim.animateTo(
            targetValue = if (isMini) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
    }
    val fraction = fractionAnim.value

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = playerState) {
            is PlayerUiState.Error -> {
                if (fraction < 1f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .alpha(1f - fraction)
                    ) {
                        // Error header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                            }
                            Text(
                                text = videoTitle, color = Color.White, fontWeight = FontWeight.Medium,
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color(0xFF111111)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF0000), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Failed to load video", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(state.message, color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.loadPlayerData(videoId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                                ) { Text("Retry") }
                            }
                        }
                    }
                } else {
                    // Mini player error state
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { onExpand() },
                        color = Color(0xFF1F1F1F),
                        shadowElevation = 8.dp
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(96.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
                                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF0000), modifier = Modifier.align(Alignment.Center))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(videoTitle, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(24.dp)) }
                        }
                    }
                }
            }

            is PlayerUiState.Loading, is PlayerUiState.Ready -> {
                val data = if (state is PlayerUiState.Ready) state.data else null
                VideoPlayerPane(
                    data = data,
                    videoTitle = videoTitle,
                    viewModel = viewModel,
                    exoPlayer = exoPlayer,
                    fraction = fraction,
                    bottomNavHeight = bottomNavHeight,
                    onBack = onBack,
                    onExpand = onExpand,
                    onClose = onClose,
                    onChannelClick = onChannelClick,
                    onRelatedVideoClick = onRelatedVideoClick,
                    onFractionChange = { newFraction ->
                        coroutineScope.launch { fractionAnim.snapTo(newFraction) }
                    },
                    onDragEnd = { shouldMinimize ->
                        coroutineScope.launch {
                            if (shouldMinimize) {
                                fractionAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                                onBack()
                            } else {
                                fractionAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                onExpand()
                            }
                        }
                    },
                    onLoadMoreComments = { viewModel.loadMoreComments() },
                    isLoadingMoreComments = viewModel.isLoadingMoreComments,
                    onPlayerSwap = { newPlayer -> exoPlayer = newPlayer },
                    isLiked = isLiked,
                    isSaved = isSaved,
                    onLikeClick = {
                        coroutineScope.launch {
                            if (playerState is com.darkk.youtube.viewmodel.PlayerUiState.Ready) {
                                val d = (playerState as com.darkk.youtube.viewmodel.PlayerUiState.Ready).data
                                val video = com.darkk.youtube.innertube.VideoItem(
                                    videoId = d.videoId, title = d.title, thumbnail = d.thumbnail,
                                    channelName = d.author, channelAvatar = d.channelAvatar,
                                    viewCount = d.views, publishedAt = d.uploadDate, duration = ""
                                )
                                isLiked = repository.toggleLike(video)
                                d.videoId.toLongOrNull()?.let { id ->
                                    try {
                                        isLiked = com.darkk.youtube.innertube.WatchSharkApi.like(id).first
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    },
                    onSaveClick = { showSaveSheet = true }
                )
            }
            else -> {}
        }
        
        if (showSaveSheet && playerState is com.darkk.youtube.viewmodel.PlayerUiState.Ready) {
            val data = (playerState as com.darkk.youtube.viewmodel.PlayerUiState.Ready).data
            val playlists by repository.playlists.collectAsState()
            val availablePlaylists = playlists.filter { it.id != "liked_videos" }
            ModalBottomSheet(
                onDismissRequest = { showSaveSheet = false },
                containerColor = Color(0xFF212121)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Save video to...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn {
                        items(availablePlaylists) { playlist ->
                            var isInPlaylist by remember { mutableStateOf(playlist.videos.any { it.videoId == videoId }) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            val video = com.darkk.youtube.innertube.VideoItem(
                                                videoId = data.videoId, title = data.title, thumbnail = data.thumbnail,
                                                channelName = data.author, channelAvatar = data.channelAvatar,
                                                viewCount = data.views, publishedAt = data.uploadDate, duration = ""
                                            )
                                            isInPlaylist = repository.toggleVideoInPlaylist(playlist.id, video)
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isInPlaylist,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(playlist.name, color = Color.White, fontSize = 16.sp)
                                if (playlist.isPrivate) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Lock, contentDescription = "Private", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VideoPlayerPane  – full player UI with swipe-down-to-mini gesture
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayerPane(
    data: PlayerData?,
    videoTitle: String,
    viewModel: com.darkk.youtube.viewmodel.YouTubeViewModel,
    exoPlayer: ExoPlayer,
    fraction: Float,
    bottomNavHeight: Dp,
    onBack: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onRelatedVideoClick: (com.darkk.youtube.innertube.VideoItem) -> Unit,
    onFractionChange: (Float) -> Unit,
    onDragEnd: (Boolean) -> Unit,
    onLoadMoreComments: () -> Unit,
    isLoadingMoreComments: Boolean,
    onPlayerSwap: (ExoPlayer) -> Unit,
    isLiked: Boolean,
    isSaved: Boolean,
    onLikeClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showCaptionMenu by remember { mutableStateOf(false) }
    var showStatsForNerds by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var selectedCaptionLang by remember { mutableStateOf("English") }
    var seekIndicator by remember { mutableStateOf<Pair<Long, Int>?>(null) } // offsetMs, direction

    LaunchedEffect(currentSpeed) {
        val params = androidx.media3.common.PlaybackParameters(currentSpeed)
        if (exoPlayer.playbackParameters.speed != currentSpeed) {
            exoPlayer.playbackParameters = params
        }
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    var floatingOffsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var floatingOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var videoScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var videoOffsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var videoOffsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var selectedQuality by remember(data) { mutableStateOf(data?.defaultQuality) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val playbackSwapScope = rememberCoroutineScope()
    var bgPlayer: ExoPlayer? by remember { mutableStateOf(null) }

    LaunchedEffect(selectedQuality) {
        val quality = selectedQuality ?: return@LaunchedEffect
        val currentId = exoPlayer.currentMediaItem?.mediaId
        
        if (quality.label != currentId) {
            bgPlayer?.release()
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(videoTitle)
                .setArtist(data?.author ?: "")
                .setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/${data?.videoId ?: ""}/hqdefault.jpg"))
                .build()
                
            val videoItem = MediaItem.Builder()
                .setUri(quality.videoUrl)
                .setMediaId(quality.label)
                .setMediaMetadata(metadata)
                .build()
            val currentPos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            val currentSpeedParams = exoPlayer.playbackParameters
            
            val isInitialLoad = currentId == null || exoPlayer.playbackState == Player.STATE_IDLE
            
            if (isInitialLoad) {
                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                
                if (quality.audioUrl != null) {
                    val audioItem = MediaItem.fromUri(quality.audioUrl)
                    val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                    val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                    val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                    exoPlayer.setMediaSource(mergedSource)
                } else {
                    exoPlayer.setMediaItem(videoItem)
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return@LaunchedEffect
            }
            
            val newPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                
                if (quality.audioUrl != null) {
                    val audioItem = MediaItem.fromUri(quality.audioUrl)
                    val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                    val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                    val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                    setMediaSource(mergedSource)
                } else {
                    setMediaItem(videoItem)
                }
                
                prepare()
                if (currentPos > 0) seekTo(currentPos)
                playWhenReady = false
                playbackParameters = currentSpeedParams
            }
            
            bgPlayer = newPlayer
            
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        val oldPlayer = exoPlayer
                        onPlayerSwap(newPlayer)
                        com.darkk.youtube.playback.PlaybackService.swapPlayer(newPlayer)
                        bgPlayer = null
                        newPlayer.removeListener(this)
                        playbackSwapScope.launch {
                            kotlinx.coroutines.delay(50)
                            oldPlayer.pause()
                            newPlayer.playWhenReady = wasPlaying
                            kotlinx.coroutines.delay(100)
                            oldPlayer.release()
                        }
                    }
                }
            }
            newPlayer.addListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose { bgPlayer?.release() }
    }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(false) }
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    LaunchedEffect(isLandscape) {
        if (activity != null) {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (isLandscape) {
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                videoScale = 1f
                videoOffsetX = 0f
                videoOffsetY = 0f
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    val nextVideo = viewModel.queueManager.getNextVideo()
                    if (nextVideo != null) {
                        viewModel.loadPlayerData(nextVideo.videoId)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        
        while(true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000L)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(3000L)
            showControls = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - fraction))) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val miniPlayerHeightPx = with(density) { 112.dp.toPx() }
        val maxDragY = screenHeight - miniPlayerHeightPx - with(density) { bottomNavHeight.toPx() + 24.dp.toPx() }
        val statusBarsHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
        val topOffset = if (isLandscape) 0f else statusBarsHeightPx * (1f - fraction)
        val expandedVideoHeight = if (isLandscape) screenHeight else (screenWidth * 9f / 16f)

        // Ambient blurred background (only visible in full screen)
        if (fraction < 1f) {
            Box(modifier = Modifier.fillMaxSize().alpha(1f - fraction)) {
                AsyncImage(
                    model = data?.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.TopCenter)
                        .blur(radius = 100.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer { alpha = 0.5f }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black), startY = 0f, endY = 1500f))
                )
            }
        }

        // ── Scrollable info + related videos ─────────────────────────────
        if (fraction < 1f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = with(density) { expandedVideoHeight.toDp() + topOffset.toDp() })
                    .offset { IntOffset(0, (fraction * screenHeight * 0.5f).roundToInt()) }
                    .alpha((1f - fraction * 3f).coerceIn(0f, 1f))
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Transparent)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (data != null) showDescriptionSheet = true }
                            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = data?.title ?: videoTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        if (data != null) {
                            Text(
                                text = "${data.author}   ${if (data.likes.isNotEmpty()) data.likes + " likes   " else ""}${if (data.views.isNotEmpty()) data.views + " views   " else ""}${if (data.uploadDate.isNotEmpty()) data.uploadDate + "   " else ""}...more",
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Loading details...",
                                color = Color(0xFFAAAAAA),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (data != null) {
                item {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                        // Author & Subscribe
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (data.channelUrl.isNotBlank()) onChannelClick(data.channelUrl) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (data.channelAvatar != null) {
                                    AsyncImage(
                                        model = data.channelAvatar,
                                        contentDescription = data.author,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.DarkGray, CircleShape)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF333333), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            data.author.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(data.author, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(data.channelSubscribers, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                            }

                            val subscriptions by viewModel.subscriptions.collectAsState()
                            val isSubscribed = subscriptions.any { it.id == data.channelUrl }

                            Button(
                                onClick = { 
                                    if (isSubscribed) {
                                        viewModel.unsubscribe(data.channelUrl)
                                    } else {
                                        viewModel.subscribe(com.darkk.youtube.innertube.SubscribedChannel(
                                            id = data.channelUrl,
                                            name = data.author,
                                            avatarUrl = data.channelAvatar,
                                            handle = null
                                        ))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) Color(0xFF272727) else Color.White,
                                    contentColor = if (isSubscribed) Color.White else Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isSubscribed) "Subscribed" else "Subscribe", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Action buttons row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val likeScale = remember { Animatable(1f) }
                                val coroutineScope = rememberCoroutineScope()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF272727), RoundedCornerShape(20.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                coroutineScope.launch {
                                                    likeScale.animateTo(1.3f, animationSpec = tween(150))
                                                    likeScale.animateTo(1f, animationSpec = tween(150))
                                                }
                                                onLikeClick()
                                            }
                                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ThumbUp,
                                            contentDescription = "Like",
                                            tint = if (isLiked) Color(0xFFFF0000) else Color.White,
                                            modifier = Modifier.size(20.dp).scale(likeScale.value)
                                        )
                                        if (data.likes.isNotEmpty() || isLiked) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (isLiked && data.likes.isEmpty()) "1" else data.likes, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        }
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF555555)))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { /* Dislike */ }
                                            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                    ) {
                                        Icon(Icons.Default.ThumbDown, contentDescription = "Dislike", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF272727), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF272727), RoundedCornerShape(20.dp))
                                        .clickable { showDownloadDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                            item {
                                val saveScale = remember { Animatable(1f) }
                                val coroutineScope = rememberCoroutineScope()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF272727), RoundedCornerShape(20.dp))
                                        .clickable { 
                                            coroutineScope.launch {
                                                saveScale.animateTo(1.3f, animationSpec = tween(150))
                                                saveScale.animateTo(1f, animationSpec = tween(150))
                                            }
                                            onSaveClick() 
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, 
                                        contentDescription = "Save", 
                                        tint = if (isSaved) Color(0xFFFF0000) else Color.White, 
                                        modifier = Modifier.size(20.dp).scale(saveScale.value)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isSaved) "Saved" else "Save", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Comments preview card
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { showCommentsSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF272727)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Comments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    if (data.exactCommentCount > 0) {
                                        Text(
                                            com.darkk.youtube.utils.FormatUtils.formatCount(data.exactCommentCount.toLong()),
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val firstComment = data.comments.firstOrNull()
                                if (firstComment != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = firstComment.authorAvatar,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF555555), CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            firstComment.text,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(24.dp).background(Color(0xFF555555), CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "This is an amazing video! Thanks for sharing.",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Quality selector moved to bottom sheet
                    }
                }

                // Related videos
                items(data.relatedVideos) { relatedVideo ->
                    VideoCard(video = relatedVideo, onClick = { onRelatedVideoClick(relatedVideo) }, onChannelAvatarClick = onChannelClick, onMoreClick = { selectedVideoForOptions = relatedVideo })
                }
                } else {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            // Skeleton Author
                            Box(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            Spacer(Modifier.height(16.dp))
                            
                            // Skeleton Author
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).shimmerEffect())
                                Spacer(Modifier.width(12.dp))
                                Box(modifier = Modifier.fillMaxWidth(0.3f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                Spacer(Modifier.weight(1f))
                                Box(modifier = Modifier.width(90.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                            }
                            Spacer(Modifier.height(24.dp))
                            
                            // Skeleton Buttons
                            Row {
                                Box(modifier = Modifier.width(100.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.width(80.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.width(80.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                            }
                        }
                    }
                    items(5) {
                        VideoSkeletonCard()
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
            }
        } // End scrollable content

        // ── Single Video Area (Transforms) ───────────────────────────────────
        var miniPlayerScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        val currentFraction by rememberUpdatedState(fraction)

        val transformableState = rememberTransformableState { zoomChange, _, _ ->
            if (currentFraction == 1f) {
                miniPlayerScale = (miniPlayerScale * zoomChange).coerceIn(1f, 3f)
            }
        }

        val baseVideoWidth = screenWidth + (with(density) { 200.dp.toPx() } - screenWidth) * fraction
        val baseVideoHeight = expandedVideoHeight + (miniPlayerHeightPx - expandedVideoHeight) * fraction
        
        val currentScale = if (fraction == 1f) miniPlayerScale else 1f
        val videoWidth = baseVideoWidth * currentScale
        val videoHeight = baseVideoHeight * currentScale
        
        val videoX = ((with(density) { 8.dp.toPx() }) * fraction) + (floatingOffsetX * fraction)
        val videoY = topOffset + (maxDragY * fraction) + (floatingOffsetY * fraction)

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { videoWidth.toDp() },
                    height = with(density) { videoHeight.toDp() }
                )
                .offset { androidx.compose.ui.unit.IntOffset(videoX.toInt(), videoY.toInt()) }
                .clip(RoundedCornerShape(if (fraction > 0f) 12.dp else 0.dp))
                .background(Color.Black)
                .transformable(transformableState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (currentFraction < 0.1f) {
                                showControls = !showControls
                            } else if (currentFraction > 0.5f) {
                                onExpand()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (currentFraction < 0.1f) {
                                val tapX = offset.x
                                val width = size.width.toFloat()
                                if (tapX < width / 3f) {
                                    // Left side - rewind 10s
                                    val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                    seekIndicator = Pair(-10000L, 0)
                                } else if (tapX > width * 2f / 3f) {
                                    // Right side - forward 10s
                                    val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                    seekIndicator = Pair(10000L, 0)
                                }
                            }
                        }
                    )
                }
                .pointerInput(isLandscape) {
                    if (isLandscape) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            videoScale = (videoScale * zoom).coerceIn(1f, 5f)
                            val maxX = (videoScale - 1f) * size.width / 2f
                            val maxY = (videoScale - 1f) * size.height / 2f
                            videoOffsetX = (videoOffsetX + pan.x * videoScale).coerceIn(-maxX, maxX)
                            videoOffsetY = (videoOffsetY + pan.y * videoScale).coerceIn(-maxY, maxY)
                        }
                    } else {
                        detectDragGestures(
                            onDragEnd = {
                                if (currentFraction < 1f) {
                                    onDragEnd(currentFraction > 0.15f)
                                } else {
                                    if (floatingOffsetY > 400f || floatingOffsetX > screenWidth / 1.5f || floatingOffsetX < -screenWidth / 1.5f) {
                                        onClose()
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (currentFraction == 1f) {
                                    floatingOffsetX += dragAmount.x
                                    floatingOffsetY += dragAmount.y
                                } else {
                                    val changeY = (dragAmount.y * 1.5f) / maxDragY
                                    onFractionChange((currentFraction + changeY).coerceIn(0f, 1f))
                                }
                            }
                        )
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = if (fraction > 0.5f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = videoScale
                        scaleY = videoScale
                        translationX = videoOffsetX
                        translationY = videoOffsetY
                    }
            )
            
            if (currentFraction > 0.9f) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .clip(CircleShape)
                            .clickable { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .clip(CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFFFF0000),
                        trackColor = Color.Transparent
                    )
                }
            }
            
            if (showControls && currentFraction < 0.1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart)
                            .then(if (isLandscape) Modifier.displayCutoutPadding() else Modifier)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                    }

                    IconButton(
                        onClick = { showSettingsMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd)
                            .then(if (isLandscape) Modifier.displayCutoutPadding() else Modifier)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    // Seek indicator overlay
                    val seekInfo = seekIndicator
                    if (seekInfo != null) {
                        LaunchedEffect(seekIndicator) {
                            kotlinx.coroutines.delay(600)
                            seekIndicator = null
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(100.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (seekInfo.first < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (seekInfo.first < 0) "-10s" else "+10s",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val totalSecs = currentPosition / 1000
                    val curH = totalSecs / 3600
                    val curM = (totalSecs % 3600) / 60
                    val curS = totalSecs % 60
                    val currentStr = if (curH > 0) String.format("%d:%02d:%02d", curH, curM, curS) else String.format("%d:%02d", curM, curS)
                    
                    val durSecs = duration / 1000
                    val durH = durSecs / 3600
                    val durM = (durSecs % 3600) / 60
                    val durS = durSecs % 60
                    val durationStr = if (durH > 0) String.format("%d:%02d:%02d", durH, durM, durS) else String.format("%d:%02d", durM, durS)
                    
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$currentStr / $durationStr",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Subtitles/Captions button
                        IconButton(
                            onClick = { showCaptionMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Captions",
                                tint = if (captionsEnabled) Color(0xFFFF0000) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // Loop toggle button
                        IconButton(
                            onClick = { isLooping = !isLooping },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Loop",
                                tint = if (isLooping) Color(0xFFFF0000) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // Fullscreen button
                        IconButton(onClick = { 
                            if (activity != null) {
                                if (isLandscape) {
                                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, 
                                contentDescription = "Fullscreen", 
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            if (currentFraction < 0.1f) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(if (showControls) 4.dp else 2.dp),
                        color = Color(0xFFFF0000),
                        trackColor = Color.White.copy(alpha = if (showControls) 0.3f else 0f)
                    )

                    Slider(
                        value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f,
                        onValueChange = { frac ->
                            exoPlayer.seekTo((frac * duration).toLong())
                            currentPosition = (frac * duration).toLong()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(24.dp)
                            .offset(y = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            }

            if (data == null) {
                CircularProgressIndicator(
                    color = Color(0xFFFF0000),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Top controls moved inside showControls
        }

        val panelHeight = with(density) { (screenHeight - (topOffset + expandedVideoHeight)).toDp() }

        // ── Description bottom sheet ──────────────────────────────────────────
        CustomSlidingPanel(
            visible = showDescriptionSheet && fraction < 0.1f,
            onDismiss = { showDescriptionSheet = false },
            height = panelHeight,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Description", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    IconButton(onClick = { showDescriptionSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))

                Text(data?.title ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Spacer(Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF272727), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (data?.likes?.isNotEmpty() == true) data.likes else "0", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Likes", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF272727), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (data?.views?.isNotEmpty() == true) data.views.replace(" views", "").replace(" views", "") else "0", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Views", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .background(Color(0xFF272727), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(data?.uploadDate ?: "-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Date", fontSize = 13.sp, color = Color(0xFFAAAAAA))
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Tags
                if (data?.tags?.isNotEmpty() == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        data.tags.take(5).forEach { tag ->
                            Surface(color = Color(0xFF272727), shape = RoundedCornerShape(16.dp)) {
                                Text(if (tag.startsWith("#")) tag else "#$tag", fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Description box (tap to expand)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF272727), RoundedCornerShape(16.dp))
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        .padding(16.dp)
                ) {
                    Text(
                        data?.description?.ifEmpty { "No description available." } ?: "No description available.",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(90.dp)
                            .background(Color.Gray, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = data?.thumbnail, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                        Text("Introduction", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(90.dp)
                            .background(Color.Gray, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = data?.thumbnail, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                        Text("Design", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Comments bottom sheet ─────────────────────────────────────────────
        CustomSlidingPanel(
            visible = showCommentsSheet && fraction < 0.1f,
            onDismiss = { showCommentsSheet = false },
            height = panelHeight,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val commentCountDisplay = if (data?.exactCommentCount != null && data.exactCommentCount > 0) {
                        "  " + com.darkk.youtube.utils.FormatUtils.formatCount(data.exactCommentCount.toLong())
                    } else ""
                    Text("Comments$commentCountDisplay", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Row {
                        IconButton(onClick = { /* Info */ }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                        IconButton(onClick = { showCommentsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Filter tabs
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Text("Top", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    Surface(color = Color(0xFF272727), shape = RoundedCornerShape(16.dp)) {
                        Text("Newest", fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Remember to keep comments respectful by following the YouTube Community Guidelines. Learn more",
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA)
                )
                Spacer(Modifier.height(16.dp))

                // Comment list
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(listState) {
                    androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { lastIndex ->
                            if (lastIndex != null && data?.comments?.isNotEmpty() == true && lastIndex >= data.comments.size - 3) {
                                onLoadMoreComments()
                            }
                        }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    if (data?.comments?.isNotEmpty() == true) {
                        items(data.comments) { comment ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                AsyncImage(
                                    model = comment.authorAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF555555), CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("${comment.author} • ${comment.time}", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                                    Spacer(Modifier.height(4.dp))
                                    Text(comment.text, fontSize = 14.sp, color = Color.White)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        if (comment.likeCount.isNotEmpty() && comment.likeCount != "0") {
                                            Spacer(Modifier.width(4.dp))
                                            Text(comment.likeCount, fontSize = 12.sp, color = Color(0xFFAAAAAA))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Icon(Icons.Default.ThumbDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(16.dp))
                                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (isLoadingMoreComments) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    } else {
                        // Empty State
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No comments available", color = Color(0xFFAAAAAA))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsMenu) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSettingsMenu = false },
            containerColor = Color(0xFF1F1F1F)
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                // Quality
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsMenu = false; showQualityMenu = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Quality", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(selectedQuality?.label ?: "Auto", color = Color.Gray, fontSize = 14.sp)
                }
                // Speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsMenu = false; showSpeedMenu = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Playback speed", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(if (currentSpeed == 1f) "Normal" else "${currentSpeed}x", color = Color.Gray, fontSize = 14.sp)
                }
                // Captions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsMenu = false; showCaptionMenu = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Subtitles, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Captions", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(if (captionsEnabled) selectedCaptionLang else "Off", color = Color.Gray, fontSize = 14.sp)
                }
                // Loop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLooping = !isLooping; showSettingsMenu = false }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Repeat, null, tint = if (isLooping) Color(0xFFFF0000) else Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Loop video", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(if (isLooping) "On" else "Off", color = Color.Gray, fontSize = 14.sp)
                }
                // Stats for nerds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsMenu = false; showStatsForNerds = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Stats for nerds", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }

    if (showCaptionMenu) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showCaptionMenu = false },
            containerColor = Color(0xFF1F1F1F)
        ) {
            val captionsList = listOf("Off", "English", "Hindi", "Spanish", "French", "German", "Japanese", "Arabic", "Portuguese")
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                item {
                    Text("Captions / Subtitles", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                }
                items(captionsList) { lang ->
                    val isSelected = if (lang == "Off") !captionsEnabled else (captionsEnabled && selectedCaptionLang == lang)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (lang == "Off") {
                                    captionsEnabled = false
                                } else {
                                    captionsEnabled = true
                                    selectedCaptionLang = lang
                                }
                                showCaptionMenu = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if ((lang == "Off" && !captionsEnabled) || (lang != "Off" && captionsEnabled && selectedCaptionLang == lang)) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                        } else {
                            Spacer(Modifier.width(36.dp))
                        }
                        Text(lang, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    if (showStatsForNerds) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showStatsForNerds = false },
            containerColor = Color(0xFF1F1F1F)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).padding(bottom = 32.dp)) {
                Text("Stats for nerds", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                val metricStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFAAAAAA), fontSize = 13.sp)
                val valueStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Resolution", style = metricStyle, modifier = Modifier.width(140.dp))
                        val v = exoPlayer.videoFormat
                        val res = if (v != null) "${v.width}x${v.height}" else "?"
                        Text(res, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Codec", style = metricStyle, modifier = Modifier.width(140.dp))
                        val v = exoPlayer.videoFormat
                        val codec = if (v != null) v.codecs ?: "?" else "?"
                        Text(codec, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Frame rate", style = metricStyle, modifier = Modifier.width(140.dp))
                        val v = exoPlayer.videoFormat
                        val fps = if (v != null && v.frameRate > 0) "${v.frameRate} fps" else "?"
                        Text(fps, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Buffer", style = metricStyle, modifier = Modifier.width(140.dp))
                        val buffered = exoPlayer.bufferedPosition
                        val bufSec = (buffered - currentPosition) / 1000
                        Text("${bufSec}s buffered", style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Bitrate", style = metricStyle, modifier = Modifier.width(140.dp))
                        val v = exoPlayer.videoFormat
                        val br = if (v != null && v.bitrate > 0) "${v.bitrate / 1000} kbps" else "?"
                        Text(br, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Audio codec", style = metricStyle, modifier = Modifier.width(140.dp))
                        val a = exoPlayer.audioFormat
                        val ac = if (a != null) a.codecs ?: "?" else "?"
                        Text(ac, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Audio channels", style = metricStyle, modifier = Modifier.width(140.dp))
                        val a = exoPlayer.audioFormat
                        val ch = if (a != null && a.channelCount > 0) "${a.channelCount}" else "?"
                        Text(ch, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Audio sample rate", style = metricStyle, modifier = Modifier.width(140.dp))
                        val a = exoPlayer.audioFormat
                        val sr = if (a != null && a.sampleRate > 0) "${a.sampleRate} Hz" else "?"
                        Text(sr, style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Volume", style = metricStyle, modifier = Modifier.width(140.dp))
                        Text("${(exoPlayer.volume * 100).toInt()}%", style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Duration", style = metricStyle, modifier = Modifier.width(140.dp))
                        val d = duration / 1000
                        val dh = d / 3600; val dm = (d % 3600) / 60; val ds = d % 60
                        Text(if (dh > 0) "${dh}:${String.format("%02d", dm)}:${String.format("%02d", ds)}" else "${dm}:${String.format("%02d", ds)}", style = valueStyle)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Real time", style = metricStyle, modifier = Modifier.width(140.dp))
                        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        Text(now, style = valueStyle)
                    }
                }
            }
        }
    }

    if (showSpeedMenu) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSpeedMenu = false },
            containerColor = Color(0xFF1F1F1F)
        ) {
            val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f, 5f)
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                item {
                    Text("Playback speed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                }
                items(speeds) { speed ->
                    val isSelected = speed == currentSpeed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentSpeed = speed
                                showSpeedMenu = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                        } else {
                            Spacer(Modifier.width(36.dp))
                        }
                        Text(
                            if (speed == 1f) "Normal" else "${speed}x",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    if (showQualityMenu) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showQualityMenu = false },
            containerColor = Color(0xFF1F1F1F)
        ) {
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                item {
                    Text("Quality for current video", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                }
                items(data?.qualities ?: emptyList()) { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedQuality = quality
                                showQualityMenu = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (quality == selectedQuality) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                        } else {
                            Spacer(Modifier.width(36.dp))
                        }
                        Text(
                            quality.label,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (quality == selectedQuality) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    selectedVideoForOptions?.let { video ->
        com.darkk.youtube.ui.components.VideoOptionsSheet(
            video = video,
            onDismissRequest = { selectedVideoForOptions = null },
            onPlayNext = {
                viewModel.queueManager.playNext(video)
                selectedVideoForOptions = null
            },
            onSaveToWatchLater = {
                coroutineScope.launch {
                    viewModel.repository.toggleVideoInPlaylist("watch_later", video)
                }
                selectedVideoForOptions = null
            },
            onSaveToPlaylist = {
                selectedVideoForOptions = null
            },
            onShare = { selectedVideoForOptions = null },
            onDownload = { isAudio, quality ->
                viewModel.downloadManager.startDownload(
                    com.darkk.youtube.download.DownloadInfo(
                        videoId = video.videoId,
                        title = video.title,
                        thumbnail = video.thumbnail,
                        channelName = video.channelName,
                        duration = video.duration,
                        quality = quality,
                        fileUri = ""
                    ))
                selectedVideoForOptions = null
            }
        )
    }

    if (showDownloadDialog && data != null) {
        com.darkk.youtube.ui.components.DownloadOptionsDialog(
            video = com.darkk.youtube.innertube.VideoItem(
                videoId = data.videoId, title = data.title, thumbnail = data.thumbnail,
                channelName = data.author, channelAvatar = data.channelAvatar,
                viewCount = data.views, publishedAt = data.uploadDate, duration = ""
            ),
            onDismissRequest = { showDownloadDialog = false },
            onDownloadConfirm = { isAudio, quality ->
                showDownloadDialog = false
                viewModel.downloadManager.startDownload(
                    com.darkk.youtube.download.DownloadInfo(
                        videoId = data.videoId,
                        title = data.title,
                        thumbnail = data.thumbnail,
                        channelName = data.author,
                        duration = "",
                        quality = if (isAudio) "audio" else quality,
                        fileUri = ""
                    )
                )
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniPlayerBar  – live video preview + title + play/pause + close button
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerBar(
    data: PlayerData,
    videoTitle: String,
    exoPlayer: ExoPlayer,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onExpand() },
        color = Color(0xFF1F1F1F),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live video thumbnail via the shared ExoPlayer
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = data.title.ifEmpty { videoTitle },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                isPlaying = !isPlaying
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CustomSlidingPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    var dragY by remember { mutableStateOf(0f) }

    val offsetY by animateDpAsState(
        targetValue = if (visible) (dragY.coerceAtLeast(0f)).dp / density.density else height,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "offsetY",
        finishedListener = {
            if (dragY >= heightPx / 3) {
                onDismiss()
            }
            dragY = 0f
        }
    )

    if (visible || offsetY < height) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = offsetY)
                .background(Color(0xFF0F0F0F))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragY > heightPx / 3) {
                                        onDismiss()
                                    } else {
                                        dragY = 0f
                                    }
                                },
                                onDragCancel = {
                                    dragY = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragY += dragAmount
                                }
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {}
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}
