package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("About", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("YouTube Premium", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("v1.0.0", color = Color.Gray, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color(0xFF272727))
            Spacer(modifier = Modifier.height(24.dp))

            AboutSection("Developer") {
                AboutInfoItem(Icons.Default.Code, "@REXXT_H4RE", "Lead Developer & Designer")
            }

            Spacer(modifier = Modifier.height(16.dp))

            AboutSection("Updates Channel") {
                AboutInfoItem(Icons.Default.Send, "@codex_update", "Official updates & releases")
                Spacer(modifier = Modifier.height(8.dp))
                AboutInfoItem(Icons.Default.Update, "Stay Updated", "Join our Telegram for latest features")
            }

            Spacer(modifier = Modifier.height(16.dp))

            AboutSection("App Info") {
                AboutInfoItem(Icons.Default.Info, "Version", "1.0.0 (Build 1)")
                Spacer(modifier = Modifier.height(8.dp))
                AboutInfoItem(Icons.Default.PhoneAndroid, "Platform", "Android 7.0+")
                Spacer(modifier = Modifier.height(8.dp))
                AboutInfoItem(Icons.Default.Storage, "Size", "~4.7 MB")
                Spacer(modifier = Modifier.height(8.dp))
                AboutInfoItem(Icons.Default.Language, "Languages", "English, Hindi")
            }

            Spacer(modifier = Modifier.height(16.dp))

            AboutSection("Release Notes") {
                Spacer(modifier = Modifier.height(8.dp))
                ReleaseNote("v1.0.0", "Initial Premium Release", listOf(
                    "Premium UI with dark theme",
                    "Ad-free experience",
                    "Fast video loading & caching",
                    "Shorts support with auto-play",
                    "Category feeds: Gaming, Music, Live, News",
                    "Full settings with 12+ sub-pages",
                    "Profile with history, playlists, downloads",
                    "Login system with history gating",
                    "Notifications page with empty state",
                    "YouTube-style bottom navigation",
                    "Developer: @REXXT_H4RE"
                ))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF272727))
            Spacer(modifier = Modifier.height(24.dp))

            Text("Made with ♥ by @REXXT_H4RE", color = Color(0xFFAAAAAA), fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("Join: @codex_update", color = Color(0xFF3EA6FF), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = Color(0xFFFF0000), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun AboutInfoItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF3EA6FF), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReleaseNote(version: String, title: String, changes: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF0000)))
            Spacer(modifier = Modifier.width(8.dp))
            Text("$version — $title", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        changes.forEach { change ->
            Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                Text("•", color = Color(0xFF3EA6FF), fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(change, color = Color(0xFFCCCCCC), fontSize = 13.sp)
            }
        }
    }
}
