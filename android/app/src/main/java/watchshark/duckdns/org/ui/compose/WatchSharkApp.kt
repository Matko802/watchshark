package watchshark.duckdns.org.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.ui.compose.theme.WatchSharkTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_WHEELS = "wheels"
private const val ROUTE_MESSAGES = "messages"
private const val ROUTE_YOU = "you"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSharkApp(
    startLoggedIn: Boolean,
) {
    val appCtx = LocalContext.current
    var themeMode by remember { mutableIntStateOf(ThemePrefs.getMode(appCtx)) }
    fun applyThemeMode(mode: Int) {
        ThemePrefs.setMode(appCtx, mode)
        themeMode = mode
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(ThemePrefs.toNightMode(mode))
    }
    // Coil loader with video-frame decoding so .webm thumbnails render.
    val videoLoader = remember(appCtx) {
        ImageLoader.Builder(appCtx)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    WatchSharkTheme(
        darkTheme = ThemePrefs.toDarkOverride(themeMode) ?: isSystemInDarkTheme(),
        amoled = themeMode == ThemePrefs.MODE_AMOLED,
    ) {
        CompositionLocalProvider(LocalImageLoader provides videoLoader) {
        val nav = rememberNavController()
        // Exposes navigation to MainActivity (launcher shortcuts).
        LaunchedEffect(nav) { AppNavigator.controller = nav }
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }
        var unread by remember { mutableIntStateOf(0) }
        var showCreateSheet by remember { mutableStateOf(false) }
        var meName by remember { mutableStateOf<String?>(null) }

        suspend fun refreshBadges() {
            try {
                unread = ApiClient.api.notifications().unread
            } catch (_: Exception) {
            }
            try {
                meName = ApiClient.api.me().user?.username
            } catch (_: Exception) {
            }
        }
        LaunchedEffect(Unit) { refreshBadges() }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: ROUTE_HOME
        val selectedTab = when {
            route.startsWith(ROUTE_HOME) -> ROUTE_HOME
            route.startsWith(ROUTE_WHEELS) -> ROUTE_WHEELS
            route.startsWith(ROUTE_MESSAGES) || route.startsWith("chat") -> ROUTE_MESSAGES
            route.startsWith(ROUTE_YOU) || route.startsWith("channel") -> ROUTE_YOU
            else -> ROUTE_HOME
        }
        val onDetail = route.startsWith("watch") || route.startsWith("chat") ||
            route.startsWith("channel") || route == "upload" ||
            route == "settings" || route == "admin" || route == "notifications"

        fun goTab(tab: String) {
            query = ""
            searchExpanded = false
            nav.navigate(tab) {
                popUpTo(nav.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            scope.launch { refreshBadges() }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (wide) {
                    // M3 Expressive collapsed NavigationRail for medium+ windows
                    // (replaces drawer, proper YT tablet pattern).
                    NavigationRail {
                        RailTab(
                            label = "Home",
                            selected = selectedTab == ROUTE_HOME,
                            selectedIcon = Icons.Filled.Home,
                            unselectedIcon = Icons.Outlined.Home,
                            onClick = { goTab(ROUTE_HOME) },
                        )
                        RailTab(
                            label = "Wheels",
                            selected = selectedTab == ROUTE_WHEELS,
                            selectedIcon = Icons.Filled.PlayArrow,
                            unselectedIcon = Icons.Outlined.PlayArrow,
                            onClick = { goTab(ROUTE_WHEELS) },
                        )
                        FloatingActionButton(onClick = { showCreateSheet = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create")
                        }
                        RailTab(
                            label = "Messages",
                            selected = selectedTab == ROUTE_MESSAGES,
                            selectedIcon = Icons.Filled.Mail,
                            unselectedIcon = Icons.Outlined.Mail,
                            onClick = { goTab(ROUTE_MESSAGES) },
                        )
                        RailTab(
                            label = "You",
                            selected = selectedTab == ROUTE_YOU,
                            selectedIcon = Icons.Filled.Person,
                            unselectedIcon = Icons.Outlined.Person,
                            onClick = {
                                val name = meName
                                if (name != null) nav.navigate("channel/$name")
                                else nav.navigate("auth")
                            },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = {
                        if (!route.startsWith("auth")) {
                            TopAppBar(
                                title = {
                                    if (searchExpanded) {
                                        TextField(
                                            value = query,
                                            onValueChange = {
                                                query = it
                                                if (selectedTab != ROUTE_HOME) goTab(ROUTE_HOME)
                                            },
                                            placeholder = { Text("Search") },
                                            singleLine = true,
                                            modifier = Modifier,
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.clickable { goTab(ROUTE_HOME) },
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.mipmap.ic_launcher),
                                                contentDescription = "WatchShark",
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape),
                                            )
                                            Text(
                                                "WatchShark",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        searchExpanded = !searchExpanded
                                        if (!searchExpanded) query = ""
                                    }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { nav.navigate("notifications") }) {
                                        BadgedBox(
                                            badge = {
                                                if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") }
                                            },
                                        ) {
                                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                                        }
                                    }
                                    IconButton(onClick = { nav.navigate("settings") }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    },
                    bottomBar = {
                        // Floating pill nav (the classic WatchShark look), built
                        // from the standard M3 Expressive NavigationBar.
                        if (!wide && !route.startsWith("auth") && !route.startsWith("chat")) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    NavigationBar(containerColor = Color.Transparent) {
                                NavigationBarItem(
                                    selected = selectedTab == ROUTE_HOME,
                                    onClick = { goTab(ROUTE_HOME) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == ROUTE_HOME) Icons.Filled.Home
                                            else Icons.Outlined.Home,
                                            contentDescription = "Home",
                                        )
                                    },
                                    label = { Text("Home") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == ROUTE_WHEELS,
                                    onClick = { goTab(ROUTE_WHEELS) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == ROUTE_WHEELS) Icons.Filled.PlayArrow
                                            else Icons.Outlined.PlayArrow,
                                            contentDescription = "Wheels",
                                        )
                                    },
                                    label = { Text("Wheels") },
                                )
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { showCreateSheet = true },
                                    icon = { Icon(Icons.Filled.Add, contentDescription = "Create") },
                                    label = { Text("Create") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == ROUTE_MESSAGES,
                                    onClick = { goTab(ROUTE_MESSAGES) },
                                    icon = {
                                        Icon(
                                            if (selectedTab == ROUTE_MESSAGES) Icons.Filled.Mail
                                            else Icons.Outlined.Mail,
                                            contentDescription = "Messages",
                                        )
                                    },
                                    label = { Text("Messages") },
                                )
                                NavigationBarItem(
                                    selected = selectedTab == ROUTE_YOU,
                                    onClick = {
                                        val name = meName
                                        if (name != null) nav.navigate("channel/$name")
                                        else nav.navigate("auth")
                                    },
                                    icon = {
                                        Icon(
                                            if (selectedTab == ROUTE_YOU) Icons.Filled.Person
                                            else Icons.Outlined.Person,
                                            contentDescription = "You",
                                        )
                                    },
                                    label = { Text("You") },
                                )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = if (startLoggedIn) ROUTE_HOME else "auth",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                query = query,
                                onOpenVideo = { v -> nav.navigate("watch/${v.id}") },
                                onOpenChannel = { name -> nav.navigate("channel/$name") },
                            )
                        }
                        composable(ROUTE_WHEELS) {
                            WheelsScreen(
                                onOpenVideo = { id -> nav.navigate("watch/$id") },
                                onOpenChannel = { name -> nav.navigate("channel/$name") },
                            )
                        }
                        composable(ROUTE_MESSAGES) {
                            MessagesScreen(onOpenThread = { name -> nav.navigate("chat/$name") })
                        }
                        composable(
                            "chat/{username}",
                            arguments = listOf(navArgument("username") { type = NavType.StringType }),
                        ) { entry ->
                            val name = entry.arguments?.getString("username") ?: return@composable
                            ChatScreen(username = name)
                        }
                        composable(ROUTE_YOU) {
                            val name = meName
                            if (name != null) {
                                ChannelScreen(
                                    username = name,
                                    onOpenVideo = { v -> nav.navigate("watch/${v.id}") },
                                )
                            } else {
                                AuthScreen(onAuthComplete = {
                                    scope.launch { refreshBadges() }
                                    goTab(ROUTE_HOME)
                                })
                            }
                        }
                        composable(
                            "watch/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: return@composable
                            WatchScreen(
                                videoId = id,
                                onOpenChannel = { name -> nav.navigate("channel/$name") },
                            )
                        }
                        composable(
                            "channel/{username}",
                            arguments = listOf(navArgument("username") { type = NavType.StringType }),
                        ) { entry ->
                            val name = entry.arguments?.getString("username") ?: return@composable
                            ChannelScreen(
                                username = name,
                                onOpenVideo = { v -> nav.navigate("watch/${v.id}") },
                            )
                        }
                        composable("upload") {
                            UploadScreen(onDone = { id -> nav.navigate("watch/$id") })
                        }
                        composable("notifications") {
                            NotificationsScreen(onOpenVideo = { id -> nav.navigate("watch/$id") })
                        }
                        composable("settings") {
                            SettingsScreen(
                                themeMode = themeMode,
                                onThemeMode = { applyThemeMode(it) },
                                onSignedOut = {
                                    scope.launch { refreshBadges() }
                                    meName = null
                                    nav.navigate("auth") {
                                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                    }
                                },
                                onOpenAdmin = { nav.navigate("admin") },
                            )
                        }
                        composable("admin") {
                            AdminScreen(
                                onOpenChannel = { name -> nav.navigate("channel/$name") },
                            )
                        }
                        composable("auth") {
                            AuthScreen(onAuthComplete = {
                                scope.launch { refreshBadges() }
                                goTab(ROUTE_HOME)
                            })
                        }
                    }
                }
            }

            if (showCreateSheet) {
                ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
                    ListItem(
                        headlineContent = { Text("Upload video / wheel") },
                        leadingContent = {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            showCreateSheet = false
                            nav.navigate("upload")
                        },
                    )
                }
            }
        }

        // Collapse search on back when on home.
        BackHandler(enabled = searchExpanded) {
            searchExpanded = false
            query = ""
        }
        @Suppress("UNUSED_VARIABLE")
        val unusedDetail = onDetail
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RailTab(
    label: String,
    selected: Boolean,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else unselectedIcon, contentDescription = label) },
        label = { Text(label) },
    )
}
