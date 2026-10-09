@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package watchshark.duckdns.org.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.AppUpdate
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.UpdateCheck
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.ui.compose.theme.AppMotion
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

    val videoLoader = remember(appCtx) {
        ImageLoader.Builder(appCtx)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    WatchSharkTheme(
        darkTheme = ThemePrefs.toDarkOverride(themeMode) ?: isSystemInDarkTheme(),
        amoled = themeMode == ThemePrefs.MODE_AMOLED,
        grey = themeMode == ThemePrefs.MODE_GREY,
    ) {
        CompositionLocalProvider(LocalImageLoader provides videoLoader) {
        val nav = rememberNavController()

        LaunchedEffect(nav) { AppNavigator.controller = nav }
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }
        var unread by remember { mutableIntStateOf(0) }
        var meName by remember { mutableStateOf<String?>(null) }
        var meAvatar by remember { mutableStateOf<String?>(null) }
        var startupUpdate by remember { mutableStateOf<AppUpdate?>(null) }
        var homeReselect by remember { mutableIntStateOf(0) }
        var wheelsReselect by remember { mutableIntStateOf(0) }

        suspend fun refreshBadges() {
            try {
                unread = ApiClient.api.notifications().unread
            } catch (_: Exception) {
            }
            try {
                val u = ApiClient.api.me().user
                meName = u?.username
                meAvatar = ApiClient.fullUrl(u?.avatar)
            } catch (_: Exception) {
            }
        }
        LaunchedEffect(Unit) { refreshBadges() }
        LaunchedEffect(Unit) {
            when (val result = Updater.checkForUpdate()) {
                is UpdateCheck.Available -> startupUpdate = result.update
                else -> {}
            }
        }

        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: ROUTE_HOME
        val selectedTab: String? = when {
            route.startsWith(ROUTE_HOME) -> ROUTE_HOME
            route.startsWith(ROUTE_WHEELS) -> ROUTE_WHEELS
            route.startsWith(ROUTE_MESSAGES) || route.startsWith("chat") -> ROUTE_MESSAGES
            route.startsWith(ROUTE_YOU) || route.startsWith("channel") -> ROUTE_YOU
            else -> null
        }

        fun goSection(route: String) {
            try {
                Haptics.tick(appCtx)
            } catch (_: Exception) {
            }
            try {
                val stackRoutes = try {
                    nav.currentBackStack.value.mapNotNull { entry ->
                        try {
                            entry.destination.route
                        } catch (_: Exception) {
                            null
                        }
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                val anchor = listOf(
                    ROUTE_HOME, ROUTE_WHEELS, ROUTE_MESSAGES, ROUTE_YOU,
                    "settings", "notifications",
                ).firstOrNull { section -> stackRoutes.any { it == section } }
                nav.navigate(route) {
                    if (anchor != null) {
                        popUpTo(anchor) { saveState = true }
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                return
            } catch (_: Exception) {
            }
            try {
                if (nav.popBackStack(route, inclusive = false)) return
            } catch (_: Exception) {
            }
            try {
                nav.navigate(route) { launchSingleTop = true }
            } catch (_: Exception) {
            }
        }

        fun goTab(tab: String) {
            query = ""
            searchExpanded = false
            val alreadyHere = try {
                nav.currentDestination?.route == tab
            } catch (_: Exception) {
                false
            }
            goSection(tab)
            if (alreadyHere) {
                if (tab == ROUTE_HOME) homeReselect++
                if (tab == ROUTE_WHEELS) wheelsReselect++
            }
            scope.launch { refreshBadges() }
        }


        fun goScreen(route: String) {
            try {
                Haptics.tick(appCtx)
            } catch (_: Exception) {
            }
            try {
                nav.navigate(route) { launchSingleTop = true }
                return
            } catch (_: Exception) {
            }
            try {
                if (nav.popBackStack(route, inclusive = false)) return
            } catch (_: Exception) {
            }
            try {
                nav.navigate(route)
            } catch (_: Exception) {
            }
        }

        fun enterHome() {
            query = ""
            searchExpanded = false
            var ok = false
            try {
                nav.navigate(ROUTE_HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
                ok = true
            } catch (_: Exception) {
            }
            if (!ok) goTab(ROUTE_HOME)
            scope.launch { refreshBadges() }
        }

        fun handleAuthComplete(loggedIn: Boolean) {
            scope.launch { refreshBadges() }
            enterHome()
            if (loggedIn) {
                AppNavigator.afterLogin?.let { pending ->
                    AppNavigator.afterLogin = null
                    goScreen(pending)
                }
            } else {
                AppNavigator.afterLogin = null
            }
        }

        LaunchedEffect(nav) {
            AppNavigator.tabHandler = { goTab(it) }
            AppNavigator.screenHandler = { goScreen(it) }
            AppNavigator.flushPending()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (wide) {


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
                        FloatingActionButton(onClick = { goSection("upload") }) {
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
                            avatarUrl = meAvatar,
                            onClick = { goTab(ROUTE_YOU) },
                        )
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = {
                        if (!route.startsWith("auth")) {
                            TopAppBar(
                                title = {
                                    AnimatedContent(
                                        targetState = searchExpanded,
                                        label = "searchToggle",
                                    ) { expanded ->
                                    if (expanded) {
                                        TextField(
                                            value = query,
                                            onValueChange = { query = it },
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
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        Haptics.tick(appCtx)
                                        if (!searchExpanded && selectedTab == null) goTab(ROUTE_HOME)
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
                                    IconButton(onClick = { goSection("notifications") }) {
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
                                    IconButton(onClick = { goSection("settings") }) {
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


                        val showPill = !wide && !route.startsWith("auth") &&
                            !route.startsWith("chat") && route != "update"
                        AnimatedVisibility(
                            visible = showPill,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            label = "pillBar",
                        ) {


                            val pillContainer = MaterialTheme.colorScheme.surfaceContainerHigh
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 19.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pillContainer,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 8.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .widthIn(max = 360.dp)
                                            .padding(
                                                horizontal = 6.dp,
                                                vertical = 6.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val selectedTabColor = MaterialTheme.colorScheme.onSurface
                                        val idleTabColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        PillTab(
                                            selected = selectedTab == ROUTE_HOME,
                                            onClick = { goTab(ROUTE_HOME) },
                                            label = "Home",
                                            selectedColor = selectedTabColor,
                                            idleColor = idleTabColor,
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_home),
                                                contentDescription = null,
                                            )
                                        }
                                        PillTab(
                                            selected = selectedTab == ROUTE_WHEELS,
                                            onClick = { goTab(ROUTE_WHEELS) },
                                            label = "Wheels",
                                            selectedColor = selectedTabColor,
                                            idleColor = idleTabColor,
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_movie),
                                                contentDescription = null,
                                            )
                                        }


                                        val createInteraction = remember { MutableInteractionSource() }
                                        val createPressed by createInteraction.collectIsPressedAsState()
                                        val createScale by animateFloatAsState(
                                            targetValue = if (createPressed) 0.8f else 1f,
                                            animationSpec = AppMotion.fastSpatial,
                                            label = "createPress",
                                        )
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .graphicsLayer {
                                                    scaleX = createScale
                                                    scaleY = createScale
                                                }
                                                .clip(CircleShape)
                                                .border(
                                                    1.5.dp,
                                                    MaterialTheme.colorScheme.outline,
                                                    CircleShape,
                                                )
                                                .clickable(
                                                    interactionSource = createInteraction,
                                                    indication = null,
                                                    role = Role.Button,
                                                    onClick = { goSection("upload") },
                                                ),
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_add),
                                                contentDescription = "Create",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        PillTab(
                                            selected = selectedTab == ROUTE_MESSAGES,
                                            onClick = { goTab(ROUTE_MESSAGES) },
                                            label = "Messages",
                                            selectedColor = selectedTabColor,
                                            idleColor = idleTabColor,
                                            badge = unread > 0,
                                            badgeText = if (unread > 9) "9+" else "$unread",
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_chat),
                                                contentDescription = null,
                                            )
                                        }
                                        PillTab(
                                            selected = selectedTab == ROUTE_YOU,
                                            onClick = { goTab(ROUTE_YOU) },
                                            label = "You",
                                            selectedColor = selectedTabColor,
                                            idleColor = idleTabColor,
                                            avatarUrl = meAvatar,
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_person),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = if (startLoggedIn) ROUTE_HOME else "auth",


                        modifier = Modifier.padding(top = padding.calculateTopPadding()),
                        enterTransition = { AppMotion.screenEnter },
                        exitTransition = { AppMotion.screenExit },
                        popEnterTransition = { AppMotion.screenPopEnter },
                        popExitTransition = { AppMotion.screenPopExit },
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                query = query,
                                reselectTick = homeReselect,
                                onOpenVideo = { v -> goScreen("watch/${v.id}") },
                                onOpenChannel = { name -> goScreen("channel/$name") },
                            )
                        }
                        composable(ROUTE_WHEELS) {
                            WheelsScreen(
                                reselectTick = wheelsReselect,
                                onOpenVideo = { id -> goScreen("watch/$id") },
                                onOpenChannel = { name -> goScreen("channel/$name") },
                            )
                        }
                        composable(ROUTE_MESSAGES) {
                            MessagesScreen(onOpenThread = { name -> goScreen("chat/$name") })
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
                                    onOpenVideo = { v -> goScreen("watch/${v.id}") },
                                )
                            } else if (!ApiClient.sessionToken().isNullOrEmpty()) {
                                var failed by remember { mutableStateOf(false) }
                                var attempt by remember { mutableIntStateOf(0) }
                                LaunchedEffect(attempt) {
                                    try {
                                        meName = ApiClient.api.me().user?.username
                                        if (meName == null) failed = true
                                    } catch (_: Exception) {
                                        failed = true
                                    }
                                }
                                if (!failed) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "Couldn't load your profile",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Button(onClick = {
                                            failed = false
                                            attempt++
                                        }) { Text("Retry") }
                                        TextButton(onClick = { goTab(ROUTE_HOME) }) {
                                            Text("Back home")
                                        }
                                    }
                                }
                            } else {
                                AuthScreen(onAuthComplete = { handleAuthComplete(it) })
                            }
                        }
                        composable(
                            "watch/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: return@composable
                            WatchScreen(
                                videoId = id,
                                onOpenChannel = { name -> goScreen("channel/$name") },
                            )
                        }
                        composable(
                            "channel/{username}",
                            arguments = listOf(navArgument("username") { type = NavType.StringType }),
                        ) { entry ->
                            val name = entry.arguments?.getString("username") ?: return@composable
                            ChannelScreen(
                                username = name,
                                onOpenVideo = { v -> goScreen("watch/${v.id}") },
                            )
                        }
                        composable("upload") {
                            UploadScreen(onDone = { id ->
                                Haptics.tick(appCtx)
                                try {
                                    nav.navigate("watch/$id") {
                                        popUpTo("upload") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } catch (_: Exception) {
                                }
                            })
                        }
                        composable("notifications") {
                            NotificationsScreen(onOpenVideo = { id -> goScreen("watch/$id") })
                        }
                        composable("settings") {
                            SettingsScreen(
                                themeMode = themeMode,
                                onThemeMode = { applyThemeMode(it) },
                                onSignedOut = {
                                    scope.launch { refreshBadges() }
                                    meName = null
                                    meAvatar = null
                                    try {
                                        nav.navigate("auth") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    } catch (_: Exception) {
                                    }
                                },
                                onOpenAdmin = { goScreen("admin") },
                                onUpdateAvailable = { goScreen("update") },
                            )
                        }
                        composable("update") {
                            UpdateScreen(onDone = {
                                try {
                                    nav.popBackStack()
                                } catch (_: Exception) {
                                }
                            })
                        }
                        composable("admin") {
                            AdminScreen(
                                onOpenChannel = { name -> goScreen("channel/$name") },
                            )
                        }
                        composable("auth") {
                            AuthScreen(onAuthComplete = { handleAuthComplete(it) })
                        }
                    }
                }
            }

            startupUpdate?.let { update ->
                Dialog(
                    onDismissRequest = { startupUpdate = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp),
                            )
                            Text(
                                "Oh no…",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "You're using an outdated client, please update!",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "v${update.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Button(
                                onClick = {
                                    Updater.pending = update
                                    startupUpdate = null
                                    goScreen("update")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) { Text("Update") }
                            TextButton(
                                onClick = { startupUpdate = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Update later") }
                        }
                    }
                }
            }
        }


        BackHandler(enabled = searchExpanded) {
            searchExpanded = false
            query = ""
        }
        }
    }
}


@Composable
private fun androidx.compose.foundation.layout.RowScope.PillTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    selectedColor: Color,
    idleColor: Color,
    badge: Boolean = false,
    badgeText: String = "",
    avatarUrl: String? = null,
    icon: @Composable () -> Unit,
) {


    val tabInteraction = remember { MutableInteractionSource() }
    val pressed by tabInteraction.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 0f,
        animationSpec = AppMotion.fastSpatial,
        label = "pillGlow",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = tabInteraction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(contentAlignment = Alignment.Center) {
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = AppMotion.fastSpatial,
                label = "pillAlpha",
            )
            val indicatorScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.6f,
                animationSpec = AppMotion.fastSpatial,
                label = "pillScale",
            )
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .graphicsLayer {
                        alpha = indicatorAlpha
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )

            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
            Box {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                    )
                } else {
                    CompositionLocalProvider(
                        LocalContentColor provides if (selected) selectedColor else idleColor,
                    ) {
                        icon()
                    }
                }
                if (badge) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (selected) selectedColor else idleColor,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RailTab(
    label: String,
    selected: Boolean,
    iconRes: Int,
    onClick: () -> Unit,
    avatarUrl: String? = null,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    painterResource(iconRes),
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        label = { Text(label) },
    )
}
