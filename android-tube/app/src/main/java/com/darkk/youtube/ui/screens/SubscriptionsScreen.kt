package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.viewmodel.SubscriptionsUiState
import com.darkk.youtube.viewmodel.YouTubeViewModel
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onVideoClick: (VideoItem) -> Unit,
    onAllClick: () -> Unit,
    onSearchActivated: () -> Unit,
    onChannelClick: (String) -> Unit,
    onNotificationsClick: () -> Unit = {}
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val feedState by viewModel.subscriptionsFeedState.collectAsState()

    val categories = listOf("All", "Today", "Videos", "Shorts", "Live", "Posts")
    var selectedCategory by remember { mutableStateOf("All") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadSubscriptionsFeed()
    }

    val isSearchActive by viewModel.isSearchActive.collectAsState()
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            onSearchActivated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar
        Surface(
            color = Color.Black,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                com.darkk.youtube.ui.components.YouTubeTopBar(
                    viewModel = viewModel,
                    onNotificationsClick = onNotificationsClick
                )
                
                // Horizontal Channels List
                if (subscriptions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(subscriptions) { channel ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(64.dp).clickable { 
                                        onChannelClick("https://www.youtube.com/channel/${channel.id}")
                                    }
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = channel.avatarUrl,
                                            contentDescription = channel.name,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Blue dot mock for unread
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF3EA6FF))
                                                .align(Alignment.BottomEnd)
                                                .offset(x = (-2).dp, y = (-2).dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = channel.name,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        TextButton(
                            onClick = onAllClick,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("All", color = Color(0xFF3EA6FF), fontWeight = FontWeight.Medium)
                        }
                    }
                }
                
                // Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = category == selectedCategory
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color.White else Color(0xFF272727),
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
            }
        }
        
        // Feed Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val state = feedState) {
                is SubscriptionsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Red
                    )
                }
                is SubscriptionsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadSubscriptionsFeed() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
                is SubscriptionsUiState.Success -> {
                    if (state.videos.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No videos found", color = Color.Gray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Subscribe to channels to see their latest videos here.",
                                color = Color.DarkGray,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 80.dp)
                            ) {
                                items(state.videos) { video ->
                                    VideoCard(video = video, onClick = { onVideoClick(video) }, onChannelAvatarClick = onChannelClick, onMoreClick = { selectedVideoForOptions = video })
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
                        }
                    }
                }
            }
        }
    }
}
