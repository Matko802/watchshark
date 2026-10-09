package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.CrashLog
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.data.MeUser
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.UpdateCheck
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.ui.compose.theme.AppMotion
import watchshark.duckdns.org.ui.httpErrorMessage

private const val SET_MAIN = "set_main"
private const val SET_ACCOUNT = "set_account"
private const val SET_APPEARANCE = "set_appearance"
private const val SET_NOTIFICATIONS = "set_notifications"
private const val SET_HAPTICS = "set_haptics"
private const val SET_ABOUT = "set_about"


@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    onSignedOut: () -> Unit,
    onOpenAdmin: () -> Unit,
    onUpdateAvailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeUser?>(null) }
    var name by remember { mutableStateOf("") }
    var curPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var notifOn by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var showCrash by remember { mutableStateOf(false) }
    var vibOn by remember { mutableStateOf(Haptics.isOn(context)) }
    var vibStr by remember { mutableFloatStateOf(Haptics.strength(context)) }

    LaunchedEffect(Unit) {
        try {
            val u = ApiClient.api.me().user
            me = u
            if (u != null) notifOn = u.notify_uploads
        } catch (_: Exception) {
        }
        showCrash = CrashLog.lastCrash(context) != null
    }

    val sub = rememberNavController()
    fun open(route: String) {
        msg = ""
        try {
            sub.navigate(route) { launchSingleTop = true }
        } catch (_: Exception) {
        }
    }
    fun back() {
        try {
            sub.popBackStack()
        } catch (_: Exception) {
        }
    }

    NavHost(
        navController = sub,
        startDestination = SET_MAIN,
        enterTransition = { AppMotion.screenEnter },
        exitTransition = { AppMotion.screenExit },
        popEnterTransition = { AppMotion.screenPopEnter },
        popExitTransition = { AppMotion.screenPopExit },
        modifier = modifier.fillMaxSize(),
    ) {
        composable(SET_MAIN) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                SettingsGroup(
                    items = listOf(
                        {
                            SettingsCategoryRow(
                                title = "Account",
                                subtitle = "Handle, password, sign out",
                                icon = Icons.Rounded.AccountCircle,
                                onClick = { open(SET_ACCOUNT) },
                            )
                        },
                        {
                            SettingsCategoryRow(
                                title = "Appearance",
                                subtitle = "System, grey, AMOLED",
                                icon = Icons.Rounded.Palette,
                                onClick = { open(SET_APPEARANCE) },
                            )
                        },
                        {
                            SettingsCategoryRow(
                                title = "Notifications",
                                subtitle = "Upload alerts",
                                icon = Icons.Rounded.Notifications,
                                onClick = { open(SET_NOTIFICATIONS) },
                            )
                        },
                        {
                            SettingsCategoryRow(
                                title = "Haptics",
                                subtitle = "Button vibration",
                                icon = Icons.Rounded.Vibration,
                                onClick = { open(SET_HAPTICS) },
                            )
                        },
                        {
                            SettingsCategoryRow(
                                title = "About",
                                subtitle = "Version, updates",
                                icon = Icons.Rounded.Info,
                                onClick = { open(SET_ABOUT) },
                            )
                        },
                    ),
                )
            }
        }

        composable(SET_ACCOUNT) {
            SettingsDetail(title = "Account", onBack = ::back) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New handle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            ApiClient.api.rename(mapOf("username" to name.trim()))
                            msg = "Name saved!"
                        } catch (e: Exception) {
                            msg = httpErrorMessage(e)
                        }
                    }
                }) { Text("Save name") }
                Text(
                    "Password",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = curPw,
                    onValueChange = { curPw = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New password (6+ chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            ApiClient.api.changePw(mapOf("current" to curPw, "password" to newPw))
                            curPw = ""
                            newPw = ""
                            msg = "Password changed."
                        } catch (e: Exception) {
                            msg = httpErrorMessage(e)
                        }
                    }
                }) { Text("Save password") }
                if (me?.admin == true) {
                    OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                        Text("Admin")
                    }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                ApiClient.api.logout()
                            } catch (_: Exception) {
                            }
                            ApiClient.clearSession()
                            UploadAlerts.cancel(context)
                            if (sub.currentDestination?.route == SET_ACCOUNT) onSignedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign out") }
                if (msg.isNotEmpty()) {
                    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        composable(SET_APPEARANCE) {
            SettingsDetail(title = "Appearance", onBack = ::back) {
                val options = listOf(
                    ThemePrefs.MODE_SYSTEM to "System default",
                    ThemePrefs.MODE_GREY to "Grey",
                    ThemePrefs.MODE_AMOLED to "AMOLED black",
                )
                options.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeMode(mode) },
                        )
                        Text(label)
                    }
                }
            }
        }

        composable(SET_NOTIFICATIONS) {
            SettingsDetail(title = "Notifications", onBack = ::back) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Switch(
                        checked = notifOn,
                        onCheckedChange = { on ->
                            notifOn = on
                            scope.launch {
                                try {
                                    ApiClient.api.notifSet(mapOf("uploads" to on))
                                    msg = "Saved!"
                                } catch (e: Exception) {
                                    msg = httpErrorMessage(e)
                                }
                            }
                        },
                    )
                    Text("Upload alerts from followed channels")
                }
                if (msg.isNotEmpty()) {
                    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        composable(SET_HAPTICS) {
            SettingsDetail(title = "Haptics", onBack = ::back) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Switch(
                        checked = vibOn,
                        onCheckedChange = { on ->
                            vibOn = on
                            Haptics.setOn(context, on)
                        },
                    )
                    Text("Button vibration")
                }
                Text(
                    "Strength",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Slider(
                        value = vibStr,
                        onValueChange = {
                            vibStr = it
                            Haptics.setStrength(context, it)
                        },
                        valueRange = 0f..100f,
                        enabled = vibOn,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${vibStr.toInt()}%")
                }
            }
        }

        composable(SET_ABOUT) {
            SettingsDetail(title = "About", onBack = ::back) {
                AppAboutCard(version = Updater.currentVersion(context))
                Text(
                    "App",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Version ${Updater.currentVersion(context)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        msg = "Checking…"
                        scope.launch {
                            when (val result = Updater.checkForUpdate()) {
                                is UpdateCheck.Available -> {
                                    Updater.pending = result.update
                                    if (sub.currentDestination?.route == SET_ABOUT) onUpdateAvailable()
                                }
                                UpdateCheck.UpToDate -> msg = "Already on the latest version"
                                is UpdateCheck.Failed -> msg = result.reason
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check for updates") }
                if (showCrash) {
                    OutlinedButton(
                        onClick = { CrashLog.showNow(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy crash report") }
                }
                if (msg.isNotEmpty()) {
                    Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


@Composable
private fun SettingsGroup(items: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val outer = 28.dp
            val inner = 4.dp
            val shape = when {
                items.size <= 1 -> RoundedCornerShape(outer)
                index == 0 -> RoundedCornerShape(
                    topStart = outer, topEnd = outer,
                    bottomStart = inner, bottomEnd = inner,
                )
                index == items.size - 1 -> RoundedCornerShape(
                    topStart = inner, topEnd = inner,
                    bottomStart = outer, bottomEnd = outer,
                )
                else -> RoundedCornerShape(inner)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                item()
            }
        }
    }
}


@Composable
private fun SettingsCategoryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
}


@Composable
private fun AppAboutCard(version: String) {
    val appCtx = LocalContext.current
    val appIcon = remember(appCtx) {
        try {
            appCtx.applicationInfo.loadIcon(appCtx.packageManager)
        } catch (_: Exception) {
            null
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = appIcon),
                contentDescription = "WatchShark",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column {
                Text("WatchShark", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun SettingsDetail(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}
