package watchshark.duckdns.org.ui.compose

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.ui.httpErrorMessage

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    content()
}

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeMode: (Int) -> Unit,
    onSignedOut: () -> Unit,
    onOpenAdmin: () -> Unit,
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

        Section("Account") {
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

        Section("Security") {
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

        Section("Notifications") {
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

        Section("Appearance") {
            val options = listOf(
                ThemePrefs.MODE_SYSTEM to "System default",
                ThemePrefs.MODE_LIGHT to "Light",
                ThemePrefs.MODE_DARK to "Dark",
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

        Section("App") {
            Text(
                "Version ${Updater.currentVersion(context)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = {
                    msg = "Checking…"
                    Updater.checkManual(context, scope) { status -> msg = status }
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

        if (msg.isNotEmpty()) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
