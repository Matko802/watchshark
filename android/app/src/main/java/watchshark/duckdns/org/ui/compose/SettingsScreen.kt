package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.CrashLog
import watchshark.duckdns.org.data.MeUser
import watchshark.duckdns.org.data.ThemePrefs
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.data.UpdateCheck
import watchshark.duckdns.org.data.UploadAlerts
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
    var pane by remember { mutableIntStateOf(0) }
    val panes = listOf("Account", "Security", "Notifications", "App")

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
        TabRow(selectedTabIndex = pane) {
            panes.forEachIndexed { i, label ->
                Tab(
                    selected = pane == i,
                    onClick = { pane = i },
                    text = { Text(label) },
                )
            }
        }
        AnimatedContent(targetState = pane, label = "settingsPane") { _ ->
        when (pane) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }

            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            3 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
