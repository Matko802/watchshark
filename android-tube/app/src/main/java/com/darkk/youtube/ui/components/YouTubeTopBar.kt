package com.darkk.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.viewmodel.YouTubeViewModel

@Composable
fun YouTubeTopBar(
    viewModel: YouTubeViewModel,
    modifier: Modifier = Modifier,
    onNotificationsClick: () -> Unit = {}
) {
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var localQuery by remember(isSearchActive, searchQuery) { mutableStateOf(if (isSearchActive) searchQuery else "") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSearchActive) {
            // YouTube Logo Custom Component
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 22.dp)
                        .background(Color(0xFFFF0000), shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "YouTube Logo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "YouTube",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.8).sp,
                        color = Color.White
                    ),
                    modifier = Modifier.scale(scaleX = 0.85f, scaleY = 1.15f).offset(x = (-4).dp)
                )
                Text(
                    text = "Premium",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(x = (-2).dp, y = 4.dp)
                )
            }
            // Right Icons
            IconButton(onClick = { /* Cast */ }) {
                Icon(Icons.Outlined.Cast, contentDescription = "Cast", tint = Color.White)
            }
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
            IconButton(onClick = { viewModel.setSearchActive(true) }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }
        } else {
            // Search bar
            IconButton(onClick = {
                viewModel.setSearchActive(false)
                localQuery = ""
                viewModel.search("")
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            BasicTextField(
                value = localQuery,
                onValueChange = { 
                    localQuery = it 
                    viewModel.updateSearchQuery(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(Color(0xFF222222), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.search(localQuery)
                    keyboardController?.hide()
                }),
                cursorBrush = SolidColor(Color.Red),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (localQuery.isEmpty()) {
                            Text("Search YouTube", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                        if (localQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    viewModel.updateSearchQuery("")
                                },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            )
        }
    }
}
