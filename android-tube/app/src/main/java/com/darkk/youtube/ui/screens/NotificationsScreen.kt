package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val icon: String,
    val isNew: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val (items, _) = com.darkk.youtube.innertube.WatchSharkApi.notifications()
            notifications = items.map { n ->
                if (n.kind == "delete") {
                    NotificationItem(
                        id = n.id.toString(),
                        title = "Video removed: ${n.title}",
                        message = n.text.ifBlank { n.createdAt },
                        time = com.darkk.youtube.innertube.WatchSharkApi.relativeTime(n.createdAt),
                        icon = "cloud_done",
                        isNew = !n.read
                    )
                } else {
                    NotificationItem(
                        id = n.id.toString(),
                        title = "@${n.username} uploaded: ${n.title}",
                        message = n.createdAt,
                        time = com.darkk.youtube.innertube.WatchSharkApi.relativeTime(n.createdAt),
                        icon = "subscriptions",
                        isNew = !n.read
                    )
                }
            }
            com.darkk.youtube.innertube.WatchSharkApi.notifRead(null)
        } catch (e: Exception) {
            notifications = emptyList()
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notifications", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No notifications yet", color = Color.Gray, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("When you get notifications, they'll show up here.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notifications) { notification ->
                    NotificationCard(notification = notification)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: NotificationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF272727)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (notification.icon) {
                    "subscriptions" -> Icons.Default.Subscriptions
                    "trending" -> Icons.Default.TrendingUp
                    "person_add" -> Icons.Default.PersonAdd
                    "reply" -> Icons.Default.Reply
                    "cloud_done" -> Icons.Default.CloudDone
                    else -> Icons.Default.Notifications
                },
                contentDescription = null,
                tint = Color(0xFF3EA6FF),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    color = Color.White,
                    fontWeight = if (notification.isNew) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp
                )
                if (notification.isNew) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF0000))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.message,
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.time,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = Color(0xFF272727), thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
}
