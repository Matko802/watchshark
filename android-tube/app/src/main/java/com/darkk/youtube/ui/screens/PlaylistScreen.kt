package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkk.youtube.data.LocalRepository
import com.darkk.youtube.data.Playlist
import com.darkk.youtube.innertube.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlist: Playlist,
    repository: LocalRepository,
    viewModel: com.darkk.youtube.viewmodel.YouTubeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val profile by repository.userProfile.collectAsState()
    val firstThumbnail = playlist.videos.firstOrNull()?.thumbnail
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Row {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                // Header Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    if (firstThumbnail != null) {
                        AsyncImage(
                            model = firstThumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(radius = 40.dp)
                                .alpha(0.5f)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F0F).copy(alpha = 0.6f))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (firstThumbnail != null) {
                            AsyncImage(
                                model = firstThumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(200.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(200.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF272727)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                }

                // Title and details
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = playlist.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00BFA5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile?.name?.take(1)?.uppercase() ?: "U",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("by ${profile?.name ?: "User"}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Playlist • ${if(playlist.isPrivate) "Private" else "Public"} • ${playlist.videos.size} videos • No views", color = Color.Gray, fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { if(playlist.videos.isNotEmpty()) onVideoClick(playlist.videos.first()) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play all", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { }, modifier = Modifier.background(Color(0xFF272727), CircleShape)) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("All", color = Color.Black) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White))
                        FilterChip(selected = false, onClick = {}, label = { Text("Videos", color = Color.White) }, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF272727)))
                        FilterChip(selected = false, onClick = {}, label = { Text("Shorts", color = Color.White) }, colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF272727)))
                    }
                }
            }

            items(playlist.videos) { video ->
                PlaylistVideoItem(video, onClick = { onVideoClick(video) }, onMoreClick = { selectedVideoForOptions = video })
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp))
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

@Composable
fun PlaylistVideoItem(video: VideoItem, onClick: () -> Unit, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(video.duration, color = Color.White, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${video.channelName} • ${video.viewCount} • ${video.publishedAt}",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onMoreClick?.invoke() }) {
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
