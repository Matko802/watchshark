package com.darkk.youtube.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.viewmodel.YouTubeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { com.darkk.youtube.data.LocalRepository(context) }

    LaunchedEffect(repository) {
        repository.loadData()
    }

    // Fetch shorts on mount
    LaunchedEffect(Unit) {
        viewModel.loadShortsFeed()
    }

    val shorts by viewModel.shortsList.collectAsState()

    if (shorts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.Red)
        }
        return
    }

    // Shared ExoPlayer specifically for shorts looping
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val pagerState = rememberPagerState(pageCount = { shorts.size })

    // Track active page changes to play/preload
    val currentShortId = shorts.getOrNull(pagerState.currentPage)?.videoId

    LaunchedEffect(currentShortId) {
        if (shorts.isNotEmpty()) {
            val currentVideo = shorts[pagerState.currentPage]
            viewModel.loadPlayerData(currentVideo.videoId)

            delay(5000)
            if (pagerState.currentPage < shorts.size && shorts[pagerState.currentPage].videoId == currentVideo.videoId) {
                repository.addToHistory(currentVideo)
            }
        }
    }

    // Preload next short for smooth swipe
    LaunchedEffect(currentShortId) {
        if (shorts.isNotEmpty()) {
            val nextIndex = pagerState.currentPage + 1
            if (nextIndex < shorts.size) {
                viewModel.preloadPlayerData(shorts[nextIndex].videoId)
            }
        }
    }

    // Connect player state stream URL to player
    val playerUiState by viewModel.playerState.collectAsState()
    LaunchedEffect(playerUiState, currentShortId) {
        if (playerUiState is com.darkk.youtube.viewmodel.PlayerUiState.Ready) {
            val data = (playerUiState as com.darkk.youtube.viewmodel.PlayerUiState.Ready).data
            if (data.videoId != currentShortId) return@LaunchedEffect
            val bestQuality = data.qualities.maxByOrNull { it.height } ?: data.defaultQuality
            
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            
            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            
            val videoItem = MediaItem.Builder()
                .setUri(bestQuality.videoUrl)
                .setMediaId(data.videoId)
                .build()
            if (bestQuality.audioUrl != null) {
                val audioItem = MediaItem.fromUri(bestQuality.audioUrl)
                val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                exoPlayer.setMediaSource(mergedSource)
            } else {
                exoPlayer.setMediaItem(videoItem)
            }
            
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val video = shorts[page]
            val isCurrentPage = pagerState.currentPage == page
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (isCurrentPage) {
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
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            if (view.player != exoPlayer) {
                                view.player = exoPlayer
                            }
                        }
                    )
                } else {
                    // Loading placeholder
                    AsyncImage(
                        model = video.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                    }
                }

                // Transparent overlay of actions and channel uploader metadata
                ShortsOverlay(
                    video = video,
                    repository = repository,
                    viewModel = viewModel,
                    onChannelClick = onChannelClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsOverlay(
    video: VideoItem,
    repository: com.darkk.youtube.data.LocalRepository,
    viewModel: YouTubeViewModel,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLiked by remember(video.videoId) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    LaunchedEffect(video.videoId) {
        isLiked = repository.isLiked(video.videoId)
    }

    val subscriptions by viewModel.subscriptions.collectAsState()
    val isSubscribed = subscriptions.any { it.id == video.channelUrl }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.3f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        // Left column metadata info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.75f)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { if (video.channelUrl.isNotEmpty()) onChannelClick(video.channelUrl) }
            ) {
                if (video.channelAvatar != null) {
                    AsyncImage(
                        model = video.channelAvatar,
                        contentDescription = video.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.DarkGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(video.channelName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = video.channelName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                // Subscribe button
                Surface(
                    onClick = {
                        scope.launch {
                            if (isSubscribed) {
                                viewModel.unsubscribe(video.channelUrl)
                            } else {
                                viewModel.subscribe(
                                    com.darkk.youtube.innertube.SubscribedChannel(
                                        id = video.channelUrl,
                                        name = video.channelName,
                                        avatarUrl = video.channelAvatar,
                                        handle = null
                                    )
                                )
                            }
                        }
                    },
                    color = if (isSubscribed) Color.White.copy(alpha = 0.2f) else Color.Red,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Original Sound - ${video.channelName}",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }

        // Right column actions overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Like
            val likeScale = remember { Animatable(1f) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    scope.launch {
                        likeScale.animateTo(1.3f, tween(150))
                        likeScale.animateTo(1f, tween(150))
                        isLiked = repository.toggleLike(video)
                        video.videoId.toLongOrNull()?.let { id ->
                            try {
                                isLiked = com.darkk.youtube.innertube.WatchSharkApi.like(id).first
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .scale(likeScale.value),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isLiked) Color.Red else Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(video.viewCount.take(4).trim(), color = Color.White, fontSize = 12.sp)
            }

            // Dislike
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.ThumbDown, contentDescription = "Dislike", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Dislike", color = Color.White, fontSize = 12.sp)
            }

            // Comments
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { showComments = true }
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Comment, contentDescription = "Comments", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Comments", color = Color.White, fontSize = 12.sp)
            }

            // Share
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Share", color = Color.White, fontSize = 12.sp)
            }

            // More Options
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
    }

    if (showComments) {
        val playerState by viewModel.playerState.collectAsState()
        val commentDataList = (playerState as? com.darkk.youtube.viewmodel.PlayerUiState.Ready)?.data?.comments ?: emptyList()
        val count = (playerState as? com.darkk.youtube.viewmodel.PlayerUiState.Ready)?.data?.exactCommentCount ?: 0
        
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = Color(0xFF0F0F0F)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Comments ($count)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(commentDataList) { comment ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            AsyncImage(
                                model = comment.authorAvatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${comment.author} • ${comment.time}", fontSize = 12.sp, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Text(comment.text, fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }
                    if (commentDataList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No comments available", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
