package com.darkk.youtube.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.data.LocalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: LocalRepository,
    onLoginSuccess: () -> Unit,
    onSkip: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fadeAnim by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { fadeAnim = true }
    LaunchedEffect(step) {
        if (step == 0) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F0F0F), Color(0xFF1A1A2E), Color(0xFF0F0F0F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = fadeAnim,
            enter = fadeIn(tween(400)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(tween(300)) + slideInHorizontally { it } togetherWith
                        fadeOut(tween(300)) + slideOutHorizontally { -it }
                    },
                    label = "step"
                ) { currentStep ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when (currentStep) {
                            0 -> {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF0000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Sign in to WatchShark", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("to like, comment, follow and upload", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(32.dp))
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; error = null },
                                    label = { Text("Email or phone", color = Color.Gray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF1E88E5),
                                        unfocusedBorderColor = Color(0xFF333333)
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                )
                                if (error != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(error!!, color = Color(0xFFFF5252), fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        if (email.isBlank()) { error = "Enter your email"; return@Button }
                                        isLoading = true
                                        scope.launch {
                                            delay(800)
                                            isLoading = false
                                            step = 1
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(25.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    else Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            1 -> {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF1E88E5), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(20.dp))
                                Text("Welcome back", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(email, color = Color.Gray, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(24.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; error = null },
                                    label = { Text("Password", color = Color.Gray) },
                                    singleLine = true,
                                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        scope.launch { performLogin(email, password, repository, onLoginSuccess, { error = it }, { isLoading = it }) }
                                    }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF1E88E5),
                                        unfocusedBorderColor = Color(0xFF333333)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color.Gray)
                                        }
                                    }
                                )
                                if (error != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(error!!, color = Color(0xFFFF5252), fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        scope.launch { performLogin(email, password, repository, onLoginSuccess, { error = it }, { isLoading = it }) }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(25.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    else Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (step == 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip & continue without account", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    }
                } else if (step == 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { step = 0; error = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private suspend fun performLogin(
    email: String,
    password: String,
    repository: LocalRepository,
    onLoginSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    if (password.isBlank()) { onError("Enter your password"); return }
    if (password.length < 6) { onError("Invalid password"); return }
    setLoading(true)
    try {
        val (id, username) = com.darkk.youtube.innertube.WatchSharkApi.login(email.trim(), password)
        repository.login(username.ifBlank { email.substringBefore("@") }, email.trim())
        setLoading(false)
        onLoginSuccess()
    } catch (e: Exception) {
        setLoading(false)
        onError(e.message ?: "Login failed")
    }
}
