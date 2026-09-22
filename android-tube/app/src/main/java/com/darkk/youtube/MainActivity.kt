package com.darkk.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.darkk.youtube.ui.components.drawBackdropCustomShape
import com.darkk.youtube.ui.components.layerBackdrop
import com.darkk.youtube.ui.components.rememberBackdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.*
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.ui.theme.YoutubeTheme
import com.darkk.youtube.viewmodel.YouTubeViewModel
import com.darkk.youtube.data.LocalRepository
import com.darkk.youtube.ui.screens.*
import kotlinx.coroutines.launch
import android.os.Build
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.darkk.youtube.innertube.WatchSharkApi.init(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            YoutubeTheme { YouTubeApp() }
        }
    }
}

@Composable
fun YouTubeApp() {
    val context = LocalContext.current
    val repository = remember { LocalRepository(context) }
    val viewModel: YouTubeViewModel = viewModel()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var currentTab by remember { mutableStateOf(0) }
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repository.loadData()
        viewModel.checkLoginState()
    }

    val profile by repository.userProfile.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val showLogin by viewModel.showLogin.collectAsState()

    val prefs = context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)
    var showWelcome by remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }

    BackHandler(enabled = activeVideo != null || currentScreen !is Screen.Home || isPlayerExpanded) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            currentScreen is Screen.PlaylistDetails -> currentScreen = Screen.Library
            currentScreen is Screen.Notifications || currentScreen is Screen.Profile -> { currentScreen = Screen.Home; currentTab = 0 }
            currentScreen is Screen.Settings || currentScreen is Screen.About -> currentScreen = Screen.Home
            currentScreen is Screen.Login -> currentScreen = Screen.Home
            currentScreen is Screen.SubSettings -> currentScreen = Screen.Settings
            currentScreen is Screen.Library || currentScreen is Screen.History || currentScreen is Screen.Downloads -> currentScreen = Screen.Home
            currentScreen !is Screen.Home -> { currentScreen = Screen.Home; currentTab = 0 }
            activeVideo != null -> { activeVideo = null }
        }
    }

    val backdrop = rememberBackdrop()

    // Handle navigation to sub-settings
    val navigateToSettings: (String) -> Unit = { settingId ->
        currentScreen = Screen.SubSettings(settingId)
    }

    // Determine bottom nav visibility
    val showBottomNav = !isPlayerExpanded && (currentScreen == Screen.Home || currentScreen == Screen.Shorts ||
        currentScreen == Screen.Notifications || currentScreen == Screen.Profile ||
        currentScreen is Screen.Library || currentScreen is Screen.Channel ||
        currentScreen is Screen.PlaylistDetails || currentScreen is Screen.Subscriptions ||
        currentScreen is Screen.AllSubscriptions)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Main content area
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            when (currentScreen) {
                is Screen.Channel -> {
                    val url = (currentScreen as Screen.Channel).channelUrl
                    ChannelScreen(url, viewModel, PaddingValues(0.dp),
                        onBack = { currentScreen = Screen.Home },
                        onVideoClick = { activeVideo = it; isPlayerExpanded = true })
                }
                is Screen.PlaylistDetails -> {
                    val pl = (currentScreen as Screen.PlaylistDetails).playlist
                    PlaylistScreen(pl, repository, viewModel, PaddingValues(0.dp),
                        onBack = { currentScreen = Screen.Library },
                        onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                        onChannelClick = { currentScreen = Screen.Channel(it) },
                        onSearchClick = { currentScreen = Screen.Home; currentTab = 0; viewModel.setSearchActive(true) })
                }
                is Screen.Subscriptions -> {
                    SubscriptionsScreen(viewModel, PaddingValues(0.dp),
                        onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                        onAllClick = { currentScreen = Screen.AllSubscriptions },
                        onSearchActivated = { currentTab = 0; currentScreen = Screen.Home },
                        onChannelClick = { currentScreen = Screen.Channel(it) },
                        onNotificationsClick = { currentScreen = Screen.Releases })
                }
                is Screen.AllSubscriptions -> {
                    AllSubscriptionsScreen(viewModel, PaddingValues(0.dp),
                        onBack = { currentScreen = Screen.Subscriptions },
                        onChannelClick = { currentScreen = Screen.Channel(it) })
                }
                is Screen.Releases -> {
                    ReleasesScreen(onBack = { currentScreen = Screen.Home }, innerPadding = PaddingValues(0.dp),
                        onVideoClick = { activeVideo = it; isPlayerExpanded = true })
                }
                is Screen.Downloads -> {
                    DownloadsScreen(viewModel, innerPadding = PaddingValues(0.dp),
                        onBack = { currentScreen = Screen.Home })
                }
                is Screen.Settings -> {
                    SettingsScreen(onBack = { currentScreen = Screen.Home }, innerPadding = PaddingValues(0.dp), onNavigate = navigateToSettings)
                }
                is Screen.SubSettings -> {
                    val id = (currentScreen as Screen.SubSettings).settingId
                    when (id) {
                        "general" -> GeneralSettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "account" -> AccountSettingsScreen(onBack = { currentScreen = Screen.Settings },
                            onLogout = { scope.launch { repository.logout(); viewModel.checkLoginState(); currentScreen = Screen.Home; currentTab = 0 } })
                        "data_saver" -> DataSaverSettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "autoplay" -> AutoplaySettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "quality" -> QualitySettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "downloads" -> DownloadSettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "tv" -> TVSettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "history" -> HistorySettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "privacy" -> PrivacySettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "notifications" -> NotifSettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "accessibility" -> AccessibilitySettingsScreen(onBack = { currentScreen = Screen.Settings })
                        "about" -> AboutScreen(onBack = { currentScreen = Screen.Settings })
                        else -> GeneralSettingsScreen(onBack = { currentScreen = Screen.Settings })
                    }
                }
                is Screen.About -> AboutScreen(onBack = { currentScreen = Screen.Home })
                is Screen.History -> HistoryScreen(viewModel, repository, PaddingValues(0.dp),
                    onBack = { currentScreen = Screen.Home },
                    onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                    onChannelClick = { currentScreen = Screen.Channel(it) })
                is Screen.Library -> LibraryScreen(repository, viewModel, PaddingValues(0.dp),
                    onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                    onPlaylistClick = { currentScreen = Screen.PlaylistDetails(it) },
                    onSearchClick = { currentScreen = Screen.Home; currentTab = 0; viewModel.setSearchActive(true) },
                    onDownloadsClick = { currentScreen = Screen.Downloads },
                    onHistoryClick = { currentScreen = Screen.History },
                    onSettingsClick = { currentScreen = Screen.Settings })
                is Screen.Home -> HomeScreen(viewModel, PaddingValues(0.dp),
                    onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                    onChannelClick = { currentScreen = Screen.Channel(it) },
                    onNotificationsClick = { currentScreen = Screen.Releases })
                is Screen.Shorts -> ShortsScreen(viewModel, PaddingValues(0.dp),
                    onChannelClick = { currentScreen = Screen.Channel(it) })
                is Screen.Notifications -> NotificationsScreen(
                    onBack = { currentScreen = Screen.Home; currentTab = 0 },
                    onSettingsClick = { currentScreen = Screen.Settings })
                is Screen.Upload -> com.darkk.youtube.ui.screens.UploadScreen(
                    onBack = { currentScreen = Screen.Home; currentTab = 0 },
                    onUploaded = { id ->
                        currentScreen = Screen.Home; currentTab = 0
                        viewModel.loadPlayerData(id.toString())
                        scope.launch {
                            try {
                                val detail = com.darkk.youtube.innertube.WatchSharkApi.videoDetail(id)
                                activeVideo = com.darkk.youtube.innertube.VideoItem(
                                    videoId = id.toString(), title = detail.video.title,
                                    thumbnail = com.darkk.youtube.innertube.WatchSharkApi.fullUrl(detail.video.thumbnail) ?: "",
                                    channelName = detail.video.username,
                                    channelAvatar = com.darkk.youtube.innertube.WatchSharkApi.fullUrl(detail.video.avatar),
                                    viewCount = "", publishedAt = "", duration = "",
                                    isShort = detail.video.kind == "wheel",
                                    channelUrl = "@${detail.video.username}"
                                )
                                isPlayerExpanded = true
                            } catch (_: Exception) {
                            }
                        }
                    })
                is Screen.Profile -> ProfileScreen(repository,
                    onBack = { currentScreen = Screen.Home; currentTab = 0 },
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onHistoryClick = { currentScreen = Screen.History },
                    onPlaylistsClick = { currentScreen = Screen.Library },
                    onDownloadsClick = { currentScreen = Screen.Downloads },
                    onLikedClick = { currentScreen = Screen.Library },
                    onAboutClick = { currentScreen = Screen.About },
                    onLoginClick = { viewModel.setShowLogin(true) })
                is Screen.Login -> LoginScreen(repository,
                    onLoginSuccess = { viewModel.checkLoginState(); currentScreen = Screen.Home; currentTab = 0 },
                    onSkip = { viewModel.setShowLogin(false); currentScreen = Screen.Home; currentTab = 0 })
                else -> HomeScreen(viewModel, PaddingValues(0.dp),
                    onVideoClick = { activeVideo = it; isPlayerExpanded = true },
                    onChannelClick = { currentScreen = Screen.Channel(it) },
                    onNotificationsClick = { currentScreen = Screen.Releases })
            }
        }

        // Player overlay
        if (activeVideo != null) {
            PlayerScreen(activeVideo!!.videoId, activeVideo!!.title, viewModel, repository,
                isMini = !isPlayerExpanded,
                bottomNavHeight = if (showBottomNav) 72.dp else 0.dp,
                onBack = { isPlayerExpanded = false },
                onExpand = { isPlayerExpanded = true },
                onClose = { activeVideo = null; isPlayerExpanded = false },
                onChannelClick = { currentScreen = Screen.Channel(it); isPlayerExpanded = false },
                onRelatedVideoClick = { activeVideo = it; isPlayerExpanded = true })
        }

        // Login dialog overlay
        if (showLogin) {
            LoginScreen(repository,
                onLoginSuccess = { viewModel.checkLoginState(); viewModel.setShowLogin(false); currentScreen = Screen.Home; currentTab = 0 },
                onSkip = { viewModel.setShowLogin(false) })
        }

        // Create bottom sheet
        if (showCreateSheet) {
            CreateScreen(onDismiss = { showCreateSheet = false }, onUploadVideo = { showCreateSheet = false; currentScreen = Screen.Upload }, onCreatePost = { }, onImportVideo = { })
        }

        // Welcome dialog
        if (showWelcome) {
            WelcomeDialog(onDismiss = { showWelcome = false; prefs.edit().putBoolean("first_launch", false).apply() })
        }

        // Bottom Navigation
        AnimatedVisibility(
            visible = showBottomNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F0F0F))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TubeNavItem(Icons.Filled.Home, Icons.Outlined.Home, "Home",
                        selected = currentTab == 0 && currentScreen == Screen.Home) {
                        currentTab = 0; currentScreen = Screen.Home
                    }
                    TubeNavItem(Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay, "Shorts",
                        selected = currentTab == 1 && currentScreen == Screen.Shorts) {
                        currentTab = 1; currentScreen = Screen.Shorts
                    }
                    TubeNavCreateItem { showCreateSheet = true }
                    TubeNavItem(Icons.Filled.Notifications, Icons.Outlined.Notifications, "Notifications",
                        selected = currentTab == 3 && currentScreen == Screen.Notifications) {
                        currentTab = 3; currentScreen = Screen.Notifications
                    }
                    TubeNavItem(Icons.Filled.Person, Icons.Outlined.Person, "Profile",
                        selected = currentTab == 4 && currentScreen == Screen.Profile) {
                        currentTab = 4; currentScreen = Screen.Profile
                    }
                }
                // Bottom safe area spacer
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TubeNavItem(
    filledIcon: androidx.compose.ui.graphics.vector.ImageVector,
    outlinedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = if (selected) filledIcon else outlinedIcon,
            contentDescription = label,
            tint = if (selected) Color.White else Color(0xFF888888),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF888888),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun TubeNavCreateItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AddCircle,
            contentDescription = "Create",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

sealed class Screen {
    object Home : Screen()
    object Shorts : Screen()
    data class Channel(val channelUrl: String) : Screen()
    object Library : Screen()
    data class PlaylistDetails(val playlist: com.darkk.youtube.data.Playlist) : Screen()
    object Subscriptions : Screen()
    object AllSubscriptions : Screen()
    object Downloads : Screen()
    object Releases : Screen()
    object History : Screen()
    object Settings : Screen()
    object Notifications : Screen()
    object Upload : Screen()
    object Profile : Screen()
    object About : Screen()
    data class SubSettings(val settingId: String) : Screen()
    object Login : Screen()
}
