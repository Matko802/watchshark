package com.darkk.youtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.innertube.VideoItem

import com.darkk.youtube.ui.components.DownloadOptionsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(
    video: VideoItem,
    onDismissRequest: () -> Unit,
    onPlayNext: () -> Unit,
    onSaveToWatchLater: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onShare: () -> Unit = {},
    onDownload: (isAudio: Boolean, quality: String) -> Unit
) {
    var showDownloadDialog by remember { mutableStateOf(false) }

    if (showDownloadDialog) {
        DownloadOptionsDialog(
            video = video,
            onDismissRequest = { showDownloadDialog = false },
            onDownloadConfirm = { isAudio, quality ->
                showDownloadDialog = false
                onDownload(isAudio, quality)
            }
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            containerColor = Color(0xFF1F1F1F),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                SheetMenuItem(icon = Icons.Default.PlaylistPlay, title = "Play next in queue", onClick = onPlayNext)
                SheetMenuItem(icon = Icons.Default.AccessTime, title = "Save to watch later", onClick = onSaveToWatchLater)
                SheetMenuItem(icon = Icons.Default.PlaylistAdd, title = "Save to playlist", onClick = onSaveToPlaylist)
                val context = androidx.compose.ui.platform.LocalContext.current
                SheetMenuItem(
                    icon = Icons.Default.Share, 
                    title = "Share", 
                    onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "https://youtube.com/watch?v=${video.videoId}")
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                        onShare()
                        onDismissRequest()
                    }
                )
                SheetMenuItem(icon = Icons.Default.Download, title = "Download", onClick = { showDownloadDialog = true })
            }
        }
    }
}

@Composable
private fun SheetMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(24.dp))
        Text(title, color = Color.White, fontSize = 16.sp)
    }
}
