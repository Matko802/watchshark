package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.darkk.youtube.download.DownloadInfo
import com.darkk.youtube.download.DownloadStatus
import com.darkk.youtube.viewmodel.YouTubeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val downloads by viewModel.downloadDatabase.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = Color.White)
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
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "No downloads",
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No downloads", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Videos that you download will appear here", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                items(downloads) { download ->
                    DownloadItemRow(
                        info = download,
                        onCancel = { viewModel.downloadManager.cancelDownload(download.videoId) },
                        onPauseResume = {
                            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING) {
                                viewModel.downloadManager.pauseDownload(download.videoId)
                            } else {
                                // Resume URL might expire, we would ideally need a background service or fresh URL.
                                // For now, we will handle cancel to retry instead, or basic resume.
                                viewModel.downloadManager.cancelDownload(download.videoId) // Fallback simple logic
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    info: DownloadInfo,
    onCancel: () -> Unit,
    onPauseResume: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = info.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (info.duration.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = info.duration,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            // Progress Bar overlay
            if (info.status == DownloadStatus.DOWNLOADING || info.status == DownloadStatus.PAUSED || info.status == DownloadStatus.PENDING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
                
                LinearProgressIndicator(
                    progress = { info.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = Color.White,
                    trackColor = Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${info.channelName} • ${info.quality}",
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val statusText = when (info.status) {
                DownloadStatus.PENDING -> "Waiting..."
                DownloadStatus.DOWNLOADING -> "Downloading ${(info.progress * 100).toInt()}%"
                DownloadStatus.PAUSED -> "Paused"
                DownloadStatus.COMPLETED -> "Downloaded"
                DownloadStatus.FAILED -> "Failed"
            }
            
            val statusColor = when (info.status) {
                DownloadStatus.COMPLETED -> Color.Gray
                DownloadStatus.FAILED -> Color.Red
                else -> Color.White
            }
            
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp
            )
        }

        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
        }
    }
}
