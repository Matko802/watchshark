package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SettingsItem(
    val id: String,
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    innerPadding: PaddingValues,
    onNavigate: (String) -> Unit = {}
) {
    val items = listOf(
        SettingsItem("general", "General", Icons.Default.Settings),
        SettingsItem("account", "Account", Icons.Default.Person),
        SettingsItem("data_saver", "Data saving", Icons.Default.SdStorage),
        SettingsItem("autoplay", "Autoplay", Icons.Default.PlayCircle),
        SettingsItem("quality", "Video quality preferences", Icons.Default.Hd),
        SettingsItem("downloads", "Downloads", Icons.Default.Download),
        SettingsItem("tv", "Watch on TV", Icons.Default.Tv),
        SettingsItem("history", "Manage all history", Icons.Default.History),
        SettingsItem("privacy", "Privacy", Icons.Default.Lock),
        SettingsItem("experimental", "Try experimental new features", Icons.Default.Science),
        SettingsItem("purchases", "Purchases and memberships", Icons.Default.CreditCard),
        SettingsItem("billing", "Billing and payments", Icons.Default.Receipt),
        SettingsItem("notifications", "Notifications", Icons.Default.Notifications),
        SettingsItem("connected", "Connected apps", Icons.Default.Link),
        SettingsItem("live_chat", "Live chat", Icons.Default.Chat),
        SettingsItem("captions", "Captions", Icons.Default.ClosedCaption),
        SettingsItem("accessibility", "Accessibility", Icons.Default.Accessible),
        SettingsItem("about", "About", Icons.Default.Info)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(items, key = { it.id }) { item ->
                Surface(
                    onClick = { onNavigate(item.id) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = Color(0xFF3EA6FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            item.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
