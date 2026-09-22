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
fun NotifSettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("Notifications", onBack) {
        SettingsToggleItem(Icons.Default.Subscriptions, "Subscriptions", "On", true)
        SettingsToggleItem(Icons.Default.Recommend, "Recommended videos", "On", true)
        SettingsToggleItem(Icons.Default.NotificationsActive, "Activity on your channel", "On", true)
        SettingsToggleItem(Icons.Default.Comment, "Replies to your comments", "On", true)
        SettingsToggleItem(Icons.Default.Share, "Shared content", "Off", true)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleItem(Icons.Default.NotificationsOff, "Do not disturb", "Off", true)
    }
}
