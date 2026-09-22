package com.darkk.youtube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YouTubeDark = darkColorScheme(
    primary = Color(0xFFFF0000),         // YouTube Red
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8B0000),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFAEAEAE),
    onSecondary = Color.Black,
    background = Color(0xFF0F0F0F),       // YouTube dark bg
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFBBBBBB),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFCF6679)
)

@Composable
fun YoutubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YouTubeDark,
        content = content
    )
}