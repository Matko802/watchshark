package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkk.youtube.viewmodel.YouTubeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSubscriptionsScreen(
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    val subscriptions by viewModel.subscriptions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("All subscriptions", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
        )

        // "Most relevant" dropdown header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Most relevant",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Sort",
                tint = Color.White,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Subscriptions List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 80.dp)
        ) {
            items(subscriptions) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick("https://www.youtube.com/channel/${channel.id}") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with dot
                    Box {
                        AsyncImage(
                            model = channel.avatarUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray),
                            contentScale = ContentScale.Crop
                        )
                        // Blue dot mock for unread
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3EA6FF))
                                .align(Alignment.BottomEnd)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Name and Handle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = channel.handle ?: "@${channel.name.replace(" ", "")}",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    // Bell Button
                    Surface(
                        color = Color(0xFF272727),
                        shape = CircleShape,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
