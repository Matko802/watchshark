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
fun QualitySettingsScreen(onBack: () -> Unit) {
    var selectedQuality by remember { mutableStateOf("Auto") }
    SettingsSubPage("Video quality preferences", onBack) {
        RadioOption("Auto", "Recommended for your connection", selectedQuality) { selectedQuality = it }
        RadioOption("1080p", "Full HD", selectedQuality) { selectedQuality = it }
        RadioOption("720p", "HD", selectedQuality) { selectedQuality = it }
        RadioOption("480p", "Standard", selectedQuality) { selectedQuality = it }
        RadioOption("360p", "Basic", selectedQuality) { selectedQuality = it }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleItem(Icons.Default.Wifi, "Limit mobile data", "Off", true)
    }
}

@Composable
private fun RadioOption(value: String, label: String, selected: String, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(value) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (value == selected) Color(0xFF1E88E5).copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.material3.Text(label, color = Color.Gray, fontSize = 12.sp)
                }
            }
            androidx.compose.material3.RadioButton(
                selected = value == selected,
                onClick = { onClick(value) },
                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Color(0xFF1E88E5))
            )
        }
    }
}
