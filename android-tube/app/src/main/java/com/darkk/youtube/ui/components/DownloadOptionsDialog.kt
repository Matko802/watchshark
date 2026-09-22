package com.darkk.youtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.innertube.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsDialog(
    video: VideoItem,
    onDismissRequest: () -> Unit,
    onDownloadConfirm: (isAudio: Boolean, quality: String) -> Unit
) {
    var isAudio by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("720p") }
    val qualities = listOf("1080p", "720p", "480p", "360p")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Download Options", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Format", color = Color.Gray, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = !isAudio,
                        onClick = { isAudio = false },
                        label = { Text("Video") }
                    )
                    FilterChip(
                        selected = isAudio,
                        onClick = { isAudio = true },
                        label = { Text("Audio") }
                    )
                }

                if (!isAudio) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Quality", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedQuality = quality }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == quality,
                                onClick = { selectedQuality = quality }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = quality, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDownloadConfirm(isAudio, if (isAudio) "audio" else selectedQuality)
            }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
