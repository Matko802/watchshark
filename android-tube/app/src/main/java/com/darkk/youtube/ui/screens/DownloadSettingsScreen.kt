package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("Downloads", onBack) {
        SettingsToggleItem(Icons.Default.Wifi, "Download over Wi-Fi only", "On", true)
        SettingsToggleItem(Icons.Default.SdStorage, "Download location", "Internal storage", false)
        SettingsToggleItem(Icons.Default.Hd, "Video quality", "720p", false)
        SettingsToggleItem(Icons.Default.AudioFile, "Audio quality", "Medium", false)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.Storage, "Manage downloads", "View and manage downloaded videos")
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.DeleteSweep, "Smart downloads", "Auto-download recommended videos")
    }
}
