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
fun AutoplaySettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("Autoplay", onBack) {
        SettingsToggleItem(Icons.Default.PlayCircle, "Autoplay video on Home", "On", true)
        SettingsToggleItem(Icons.Default.PlayCircle, "Autoplay Shorts", "On", true)
        SettingsToggleItem(Icons.Default.PlaylistPlay, "Autoplay next video", "Off", true)
    }
}
