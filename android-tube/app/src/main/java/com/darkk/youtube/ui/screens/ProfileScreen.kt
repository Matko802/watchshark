package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.data.LocalRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: LocalRepository,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onLikedClick: () -> Unit = {},
    onYourChannelClick: () -> Unit = {},
    onYourVideosClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    val profile by repository.userProfile.collectAsState()
    val isLoggedIn = profile?.isLoggedIn == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Account", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Profile avatar + info
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(80.dp).clip(CircleShape).background(if (isLoggedIn) Color(0xFFFF0000) else Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn) {
                    Text(
                        profile?.name?.take(1)?.uppercase() ?: "U",
                        color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isLoggedIn) profile?.name ?: "User" else "Sign in to YouTube Premium",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                if (isLoggedIn) {
                    Text(profile?.handle ?: "@user", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF272727)) {
                        Text("Manage your Google Account", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap to sign in and get the full experience", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLoginClick,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Sign in", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoggedIn) {
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("0", "Subscribers")
                StatItem("0", "Videos")
                StatItem("0", "Views")
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFF272727))
            Spacer(modifier = Modifier.height(8.dp))

            // Content sections
            ProfileMenuItem(Icons.Default.Person, "Your channel", "Manage your channel", onClick = onYourChannelClick)
            ProfileMenuItem(Icons.Default.History, "History", "View watch history", onClick = onHistoryClick)
            ProfileMenuItem(Icons.Default.PlaylistPlay, "Playlists", "Your curated content", onClick = onPlaylistsClick)
            ProfileMenuItem(Icons.Default.VideoLibrary, "Your videos", "Uploaded by you", onClick = onYourVideosClick)
            ProfileMenuItem(Icons.Default.Download, "Downloads", "Offline content", onClick = onDownloadsClick)
            ProfileMenuItem(Icons.Default.ThumbUp, "Liked videos", "Videos you liked", onClick = onLikedClick)

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF272727))
            Spacer(modifier = Modifier.height(8.dp))

            // About section
            Text("About", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            ProfileMenuItem(Icons.Default.Info, "About", "YouTube Premium v1.0", onClick = onAboutClick)
            ProfileMenuItem(Icons.Default.BugReport, "Report a problem", "Send feedback", onClick = { })
            ProfileMenuItem(Icons.Default.HelpOutline, "Help", "Get support", onClick = { })
        } else {
            // Guest view
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("You're browsing as a guest", color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sign in to access history, playlists, downloads & more", color = Color(0xFF777777), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("@REXXT_H4RE", color = Color(0xFFFF0000), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp)
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}
