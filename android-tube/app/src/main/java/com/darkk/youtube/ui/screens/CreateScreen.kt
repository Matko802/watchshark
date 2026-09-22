package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onDismiss: () -> Unit,
    onUploadVideo: () -> Unit,
    onCreatePost: () -> Unit,
    onImportVideo: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F1F1F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Create", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Upload or create content", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            CreateOption(
                icon = Icons.Default.Videocam,
                title = "Upload Video",
                subtitle = "Share a video to your channel",
                color = Color(0xFFFF0000),
                onClick = {
                    onDismiss()
                    onUploadVideo()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CreateOption(
                icon = Icons.Default.Article,
                title = "Create Post",
                subtitle = "Share thoughts, updates, or polls",
                color = Color(0xFF3EA6FF),
                onClick = {
                    onDismiss()
                    onCreatePost()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            CreateOption(
                icon = Icons.Default.FileDownload,
                title = "Import Video",
                subtitle = "Import from device storage",
                color = Color(0xFF00BFA5),
                onClick = {
                    onDismiss()
                    onImportVideo()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CreateOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
    }
}
