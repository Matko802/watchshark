package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkk.youtube.innertube.ChannelData
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.ui.screens.VideoCard
import com.darkk.youtube.viewmodel.ChannelUiState
import com.darkk.youtube.viewmodel.YouTubeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val channelState by viewModel.channelState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(channelUrl) {
        viewModel.loadChannelData(channelUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.setSearchActive(true)
                        onBack()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (channelState) {
                is ChannelUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                is ChannelUiState.Error -> {
                    Text(
                        text = (channelState as ChannelUiState.Error).message,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ChannelUiState.Success -> {
                    val data = (channelState as ChannelUiState.Success).data
                    ChannelContent(data, innerPadding, viewModel, onVideoClick)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ChannelContent(
    data: ChannelData,
    innerPadding: PaddingValues,
    viewModel: YouTubeViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Videos", "Shorts", "Playlists", "Posts")
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Banner
            if (data.bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = data.bannerUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.DarkGray)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // Profile Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = data.avatarUrl,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = data.name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (data.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "${data.handle.ifBlank { "@" + data.name.replace(" ", "") }}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${data.subscribers} • ${data.videos.size} videos", // Using loaded videos count as fallback
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            // Description
            if (data.description.isNotBlank()) {
                Text(
                    text = data.description.replace("\n", " ") + " ...more",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { /* Expand description */ }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            val subscriptions by viewModel.subscriptions.collectAsState()
            val isSubscribed = subscriptions.any { it.id == data.id }

            // Subscribe Button
            Button(
                onClick = { 
                    if (isSubscribed) {
                        viewModel.unsubscribe(data.id)
                    } else {
                        viewModel.subscribe(com.darkk.youtube.innertube.SubscribedChannel(
                            id = data.id,
                            name = data.name,
                            avatarUrl = data.avatarUrl,
                            handle = data.handle.ifBlank { null }
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSubscribed) Color(0xFF272727) else Color.White,
                    contentColor = if (isSubscribed) Color.White else Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Black,
                contentColor = Color.White,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color.White else Color.Gray
                            )
                        }
                    )
                }
            }
            Divider(color = Color.DarkGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (selectedTab) {
            1 -> { // Videos
                items(data.videos.filter { !it.isShort }) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) }, onMoreClick = { selectedVideoForOptions = video })
                }
            }
            2 -> { // Shorts
                val shorts = data.videos.filter { it.isShort }
                if (shorts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No shorts available", color = Color.Gray)
                        }
                    }
                } else {
                    items(shorts.chunked(2)) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (short in rowItems) {
                                Box(modifier = Modifier.weight(1f)) {
                                    com.darkk.youtube.ui.screens.ShortCard(video = short, onClick = { onVideoClick(short) })
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            3 -> { // Playlists
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Playlists coming soon...", color = Color.Gray)
                    }
                }
            }
            4 -> { // Posts
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Posts coming soon...", color = Color.Gray)
                    }
                }
            }
            else -> { // Home
                items(data.videos) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) }, onMoreClick = { selectedVideoForOptions = video })
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
}
