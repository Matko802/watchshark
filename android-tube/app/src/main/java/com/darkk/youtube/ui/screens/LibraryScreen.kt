package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.darkk.youtube.data.Playlist
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.viewmodel.YouTubeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    repository: LocalRepository,
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onVideoClick: (VideoItem) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val profile by repository.userProfile.collectAsState()
    val history by repository.history.collectAsState()
    val playlists by repository.playlists.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Color.White)
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.clickable { onSearchClick() })
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.clickable { onSettingsClick() })
            }
        }

        // Profile Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00BFA5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile?.name?.take(1)?.uppercase() ?: "U",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = profile?.name ?: "User", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profile?.handle ?: "@user", color = Color.Gray, fontSize = 14.sp)
                    Text(" • ", color = Color.Gray)
                    Text("View channel", color = Color.Gray, fontSize = 14.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History Section
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("History", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { video ->
                    HistoryVideoItem(video, onVideoClick, onMoreClick = { selectedVideoForOptions = video })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Playlists Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
            }
            var showCreatePlaylistDialog by remember { mutableStateOf(false) }
            var newPlaylistName by remember { mutableStateOf("") }
            
            Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = Color.White, modifier = Modifier.clickable { showCreatePlaylistDialog = true })
            
            if (showCreatePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("New Playlist", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Title", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                coroutineScope.launch {
                                    repository.createPlaylist(newPlaylistName)
                                    showCreatePlaylistDialog = false
                                    newPlaylistName = ""
                                }
                            }
                        }) {
                            Text("CREATE", color = Color(0xFF3EA6FF))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("CANCEL", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF212121)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        // Playlists Grid (Horizontal scroll for playlists to match screenshot style or grid)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists) { playlist ->
                PlaylistCard(playlist, onClick = { onPlaylistClick(playlist) })
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom Menu Items
        LibraryMenuItem(icon = Icons.Default.PlayArrow, title = "Your videos")
        LibraryMenuItem(icon = Icons.Default.Download, title = "Downloads", onClick = onDownloadsClick)
        LibraryMenuItem(icon = Icons.Default.Movie, title = "Films")
        LibraryMenuItem(icon = Icons.Default.Stars, title = "Badges")
        
        Spacer(modifier = Modifier.height(100.dp))
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

@Composable
fun HistoryVideoItem(video: VideoItem, onClick: (VideoItem) -> Unit, onMoreClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick(video) }
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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
                    text = video.channelName,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onMoreClick?.invoke() }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF272727)),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.videos.isNotEmpty()) {
                AsyncImage(
                    model = playlist.videos.first().thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay for playlist style
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (playlist.id == "watch_later") Icons.Default.AccessTime else if (playlist.id == "liked_videos") Icons.Default.ThumbUp else Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White
                )
                Text("${playlist.videos.size}", color = Color.White, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = playlist.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = if (playlist.isPrivate) "Private" else "Public", color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LibraryMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Text(title, color = Color.White, fontSize = 16.sp)
    }
}
