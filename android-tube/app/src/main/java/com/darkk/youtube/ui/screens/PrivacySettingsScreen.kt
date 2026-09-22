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
fun PrivacySettingsScreen(onBack: () -> Unit) {
    SettingsSubPage("Privacy", onBack) {
        SettingsToggleItem(Icons.Default.Visibility, "Keep all my subscriptions private", "Off", true)
        SettingsToggleItem(Icons.Default.Lock, "Keep all my liked videos private", "Off", true)
        SettingsToggleItem(Icons.Default.PersonSearch, "Allow search history tracking", "On", true)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.Shield, "Ad personalization", "Manage ad preferences")
        Spacer(modifier = Modifier.height(8.dp))
        ClickableSettingsItem(Icons.Default.Policy, "Privacy policy", "How we handle your data")
    }
}
