package com.darkk.youtube.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    if (dismissed) return

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.85f, animationSpec = tween(500, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(enabled = false) { }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F0F0F))
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Welcome to YouTube Premium",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            FeatureRow("Ad-Free Experience")
                            Spacer(modifier = Modifier.height(10.dp))
                            FeatureRow("Ultra Fast Video Loading")
                            Spacer(modifier = Modifier.height(10.dp))
                            FeatureRow("Music & Shorts Support")
                            Spacer(modifier = Modifier.height(10.dp))
                            FeatureRow("Better Performance & Caching")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Developer:", color = Color.Gray, fontSize = 13.sp)
                            Text("@REXXT_H4RE", color = Color(0xFFFF0000), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Official Updates Channel:", color = Color.Gray, fontSize = 13.sp)
                            Text("@codex_update", color = Color(0xFF3EA6FF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/codex_update")))
                        } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join Updates Channel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        dismissed = true
                        onDismiss()
                    }) {
                        Text("Get Started", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("  $text", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
