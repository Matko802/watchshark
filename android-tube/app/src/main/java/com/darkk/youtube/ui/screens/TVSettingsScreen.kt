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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVSettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Watch on TV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Watch on your TV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Connect to a TV to watch videos on a bigger screen", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Icon(Icons.Default.Cast, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect to TV", fontSize = 16.sp)
                }
            }
        }
    }
}
