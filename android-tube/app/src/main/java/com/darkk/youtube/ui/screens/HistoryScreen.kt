package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
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
import com.darkk.youtube.data.LocalRepository
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.viewmodel.YouTubeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: YouTubeViewModel,
    repository: LocalRepository,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val history by repository.history.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "History",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (history.isNotEmpty()) {
                IconButton(onClick = {
                    coroutineScope.launch {
                        // TODO: Implement clear history in repository
                        // repository.clearHistory()
                    }
                }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History", tint = Color.White)
                }
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "This list has no videos.", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(history) { video ->
                    HistoryItem(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onMoreClick = { selectedVideoForOptions = video }
                    )
                }
            }
        }
    }

    if (selectedVideoForOptions != null) {
        val video = selectedVideoForOptions!!
        com.darkk.youtube.ui.components.VideoOptionsSheet(
            video = video,
            onDismissRequest = { selectedVideoForOptions = null },
            onPlayNext = {
                viewModel.queueManager.playNext(video)
                selectedVideoForOptions = null
            },
            onSaveToWatchLater = {
                coroutineScope.launch {
                    repository.toggleVideoInPlaylist("watch_later", video)
                }
                selectedVideoForOptions = null
            },
            onSaveToPlaylist = {
                selectedVideoForOptions = null
                // Note: Full save to arbitrary playlist would require a bottom sheet to select playlist.
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

@Composable
fun HistoryItem(
    video: VideoItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF272727))
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = video.duration,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            // Progress Bar (Mocked to 50%)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Gray.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .background(Color.Red)
                )
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
                text = "${video.channelName} • ${video.viewCount}",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
        }
    }
}
