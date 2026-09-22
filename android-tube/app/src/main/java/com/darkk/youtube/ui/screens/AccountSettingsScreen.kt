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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    SettingsSubPage("Account", onBack) {
        ClickableSettingsItem(Icons.Default.Person, "Google Account", "Manage your account")
        ClickableSettingsItem(Icons.Default.SwitchAccount, "Switch account", "Add or switch accounts")
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFF272727))
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFF0000).copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out", color = Color(0xFFFF5252), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
