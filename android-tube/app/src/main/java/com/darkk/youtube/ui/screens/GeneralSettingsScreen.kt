package com.darkk.youtube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("General", onBack) {
        SettingsToggleItem(Icons.Default.Language, "App Language", "English", false)
        SettingsToggleItem(Icons.Default.LocationOn, "Location", "India", false)
        SettingsToggleItem(Icons.Default.Wifi, "Limit mobile data usage", "Off", true)
        SettingsToggleItem(Icons.Default.Storage, "Storage", "Manage device storage", false)
    }
}
