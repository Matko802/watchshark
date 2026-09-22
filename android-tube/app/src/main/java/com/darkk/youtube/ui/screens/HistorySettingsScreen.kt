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
fun HistorySettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("Manage history", onBack) {
        SettingsToggleItem(Icons.Default.History, "Pause watch history", "Off", true)
        SettingsToggleItem(Icons.Default.Search, "Pause search history", "Off", true)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.DeleteSweep, "Clear watch history", "Remove all watch history")
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.Delete, "Clear search history", "Remove all search history")
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.AutoDelete, "Auto-delete", "Auto-delete history older than 3 months")
    }
}
