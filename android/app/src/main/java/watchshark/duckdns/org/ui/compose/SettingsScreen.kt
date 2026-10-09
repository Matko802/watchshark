package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.CrashLog
import watchshark.duckdns.org.data.MeUser
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.data.UpdateCheck
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.ui.compose.theme.AppMotion
import watchshark.duckdns.org.ui.httpErrorMessage

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
    // Accordion: tapping a section force-opens it and closes the other one.
    var openSection by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val u = ApiClient.api.me().user
            me = u
            if (u != null) notifOn = u.notify_uploads
        } catch (_: Exception) {
        }
        showCrash = CrashLog.lastCrash(context) != null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingsSection(
            title = "Account",
            iconRes = R.drawable.ic_person,
            open = openSection == 0,
            onOpen = { openSection = 0 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onSignedOut()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
            }
        }
        SettingsSection(
            title = "Notifications",
            iconRes = R.drawable.ic_notifications,
            open = openSection == 1,
            onOpen = { openSection = 1 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
        SettingsSection(
            title = "App",
            iconRes = R.drawable.ic_settings,
            open = openSection == 2,
            onOpen = { openSection = 2 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

                Text(
                    "App",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
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
                                onUpdateAvailable()
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
            }
        }
        AnimatedVisibility(
            visible = msg.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "settingsMsg",
        ) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One accordion section: tapping the header force-opens it (and the
 * open one closes), arrow rotates, body expands with a spring.
 * No highlight ripple — motion is the feedback.
 */
@Composable
private fun SettingsSection(
    title: String,
    iconRes: Int,
    open: Boolean,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    val arrow by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = AppMotion.fastSpatial,
        label = "sectionArrow",
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrow },
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                label = "sectionBody",
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    content()
                }
            }
        }
    }
}
