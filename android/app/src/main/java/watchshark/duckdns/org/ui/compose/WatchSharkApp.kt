@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.R
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
    val appIcon = remember { appCtx.applicationInfo.loadIcon(appCtx.packageManager) }
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
                            iconRes = R.drawable.ic_home,
                            onClick = { goTab(ROUTE_HOME) },
                        )
                        RailTab(
                            label = "Wheels",
                            selected = selectedTab == ROUTE_WHEELS,
                            iconRes = R.drawable.ic_movie,
                            onClick = { goTab(ROUTE_WHEELS) },
                        )
                        FloatingActionButton(onClick = { showCreateSheet = true }) {
                            Icon(
                                painterResource(R.drawable.ic_add),
                                contentDescription = "Create",
                            )
                        }
                        RailTab(
                            label = "Messages",
                            selected = selectedTab == ROUTE_MESSAGES,
                            iconRes = R.drawable.ic_chat,
                            onClick = { goTab(ROUTE_MESSAGES) },
                        )
                        RailTab(
                            label = "You",
                            selected = selectedTab == ROUTE_YOU,
                            iconRes = R.drawable.ic_person,
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
                                                painter = rememberAsyncImagePainter(
                                                    model = appIcon,
                                                ),
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
                                        Icon(
                                            painterResource(R.drawable.ic_search),
                                            contentDescription = "Search",
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { nav.navigate("notifications") }) {
                                        BadgedBox(
                                            badge = {
                                                if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") }
                                            },
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_notifications),
                                                contentDescription = "Notifications",
                                            )
                                        }
                                    }
                                    IconButton(onClick = { nav.navigate("settings") }) {
                                        Icon(
                                            painterResource(R.drawable.ic_settings),
                                            contentDescription = "Settings",
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    },
                    bottomBar = {
                        // Slim centered slider pill, same language as the
                        // Latest / Trending switch: 5 equal segments, plain
                        // icons (no check marks), logo as the Create segment.
                        if (!wide && !route.startsWith("auth") && !route.startsWith("chat") && route != "update") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp)
                                    .padding(bottom = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    SegmentedButton(
                                        selected = selectedTab == ROUTE_HOME,
                                        onClick = { goTab(ROUTE_HOME) },
                                        shape = SegmentedButtonDefaults.itemShape(0, 5),
                                        icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_home),
                                            contentDescription = null,
                                        )
                                        },
                                        label = { Text("Home") },
                                    )
                                    SegmentedButton(
                                        selected = selectedTab == ROUTE_WHEELS,
                                        onClick = { goTab(ROUTE_WHEELS) },
                                        shape = SegmentedButtonDefaults.itemShape(1, 5),
                                        icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_movie),
                                            contentDescription = null,
                                        )
                                        },
                                        label = { Text("Wheels") },
                                    )
                                    SegmentedButton(
                                        selected = false,
                                        onClick = { showCreateSheet = true },
                                        shape = SegmentedButtonDefaults.itemShape(2, 5),
                                        icon = {
                                            Image(
                                                painter = rememberAsyncImagePainter(
                                                    model = appIcon,
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape),
                                            )
                                        },
                                        label = { Text("Create") },
                                    )
                                    SegmentedButton(
                                        selected = selectedTab == ROUTE_MESSAGES,
                                        onClick = { goTab(ROUTE_MESSAGES) },
                                        shape = SegmentedButtonDefaults.itemShape(3, 5),
                                        icon = {
                                        Box {
                                            Icon(
                                                painterResource(R.drawable.ic_chat),
                                                contentDescription = null,
                                            )
                                            if (unread > 0) {
                                                Badge(
                                                    modifier = Modifier.align(Alignment.TopEnd),
                                                ) {
                                                    Text(if (unread > 9) "9+" else "$unread")
                                                }
                                            }
                                        }
                                        },
                                        label = { Text("Messages") },
                                    )
                                    SegmentedButton(
                                        selected = selectedTab == ROUTE_YOU,
                                        onClick = {
                                            val name = meName
                                            if (name != null) nav.navigate("channel/$name")
                                            else nav.navigate("auth")
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(4, 5),
                                        icon = {
                                            Icon(
                                                painterResource(R.drawable.ic_person),
                                                contentDescription = null,
                                            )
                                        },
                                        label = { Text("You") },
                                    )
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
                                onUpdateAvailable = { nav.navigate("update") },
                            )
                        }
                        composable("update") {
                            UpdateScreen(onDone = { nav.popBackStack() })
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
                            Icon(
                                painterResource(R.drawable.ic_add),
                                contentDescription = null,
                            )
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
    iconRes: Int,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painterResource(iconRes),
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        label = { Text(label) },
    )
}
