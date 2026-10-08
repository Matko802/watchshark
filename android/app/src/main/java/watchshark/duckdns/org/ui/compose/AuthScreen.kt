package watchshark.duckdns.org.ui.compose

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.ui.apiErrorMessage
import watchshark.duckdns.org.ui.httpErrorMessage

@Composable
fun AuthScreen(
    onAuthComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modeLogin by remember { mutableStateOf(true) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    fun afterAuth() {
        scope.launch {
            try {
                val me = ApiClient.api.me().user
                if (me == null) {
                    err = "Login failed"
                    return@launch
                }
                if (me.deleted) {
                    err = "Your account has been deleted." +
                        (me.deleted_reason?.let { "\nReason: $it" } ?: "")
                    ApiClient.clearSession()
                    return@launch
                }
                if (me.banned) {
                    val days = if (me.ban_days_left < 0) "permanently"
                    else "for %.1f days".format(me.ban_days_left)
                    err = "You have been banned $days." +
                        (me.ban_reason?.let { "\nReason: $it" } ?: "")
                    return@launch
                }
                UploadAlerts.ensureScheduled(context)
                onAuthComplete()
            } catch (e: Exception) {
                err = httpErrorMessage(e)
            }
        }
    }

    fun doLogin() {
        scope.launch {
            try {
                val res = ApiClient.api.login(mapOf("login" to login, "password" to password))
                if (res.has("error")) {
                    err = res.get("error").asString
                    return@launch
                }
                afterAuth()
            } catch (e: Exception) {
                err = httpErrorMessage(e)
            }
        }
    }

    fun doSignup() {
        scope.launch {
            try {
                val res = ApiClient.api.signup(
                    mapOf("username" to username, "email" to email, "password" to password),
                )
                if (res.has("error")) {
                    err = res.get("error").asString
                    return@launch
                }
                if (res.has("verify")) {
                    err = "Account created — waiting for admin approval."
                    modeLogin = true
                } else {
                    afterAuth()
                }
            } catch (e: Exception) {
                err = apiErrorMessage(e)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (modeLogin) "Log in" else "Create account",
            style = MaterialTheme.typography.headlineMedium,
        )
        if (!modeLogin) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Handle (letters, numbers, _)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = if (modeLogin) login else password,
            onValueChange = { if (modeLogin) login = it else password = it },
            label = { Text(if (modeLogin) "Handle or email" else "Password (6+ chars)") },
            singleLine = true,
            visualTransformation = if (modeLogin) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (modeLogin) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (err.isNotEmpty()) {
            Text(err, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { if (modeLogin) doLogin() else doSignup() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (modeLogin) "Log in" else "Create account")
        }
        TextButton(onClick = { modeLogin = !modeLogin; err = "" }) {
            Text(if (modeLogin) "Create account" else "Log in")
        }
        if (modeLogin) {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            (ApiClient.BASE_URL.trimEnd('/') + "/forgot").toUri(),
                        ),
                    )
                } catch (e: Exception) {
                    err = apiErrorMessage(e)
                }
            }) {
                Text("Forgot password?")
            }
        }
        TextButton(onClick = onAuthComplete) {
            Text("Continue as guest")
        }
    }
}
