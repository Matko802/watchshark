package watchshark.duckdns.org.ui.compose

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.UploadAlerts
import watchshark.duckdns.org.ui.apiErrorMessage
import watchshark.duckdns.org.ui.httpErrorMessage

@Composable
fun AuthScreen(
    onAuthComplete: (Boolean) -> Unit,
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
    var busy by remember { mutableStateOf(false) }
    var showPw by remember { mutableStateOf(false) }

    fun validEmail(v: String): Boolean {
        return Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(v.trim())
    }
    fun validHandle(v: String): Boolean {
        return Regex("^[A-Za-z0-9_]{3,30}$").matches(v.trim())
    }

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
                (context as? android.app.Activity)?.let {
                    UploadAlerts.ensureScheduled(it)
                }
                onAuthComplete(true)
            } catch (e: Exception) {
                err = httpErrorMessage(e)
            }
        }
    }

    fun doLogin() {
        if (busy) return
        if (login.trim().isEmpty() || password.isEmpty()) {
            err = "Enter email and password"
            return
        }
        busy = true
        err = ""
        scope.launch {
            try {
                val res = ApiClient.api.login(mapOf("login" to login.trim(), "password" to password))
                if (res.has("error")) {
                    err = res.get("error").asString
                    return@launch
                }
                afterAuth()
            } catch (e: Exception) {
                err = httpErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    fun doSignup() {
        if (busy) return
        if (!validHandle(username)) {
            err = "Handle needs 3-30 letters, numbers or _"
            return
        }
        if (!validEmail(email)) {
            err = "Enter a valid email"
            return
        }
        if (password.length < 6) {
            err = "Password needs 6+ characters"
            return
        }
        busy = true
        err = ""
        scope.launch {
            try {
                val res = ApiClient.api.signup(
                    mapOf("username" to username.trim(), "email" to email.trim().lowercase(), "password" to password),
                )
                if (res.has("error")) {
                    err = res.get("error").asString
                    return@launch
                }
                if (res.has("verify")) {
                    err = "Account created, waiting for admin approval."
                    modeLogin = true
                } else {
                    afterAuth()
                }
            } catch (e: Exception) {
                err = apiErrorMessage(e)
            } finally {
                busy = false
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
        AnimatedContent(targetState = modeLogin, label = "authMode") { loginMode ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!loginMode) {
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
            value = if (loginMode) login else password,
            onValueChange = { if (loginMode) login = it else password = it },
            label = { Text(if (loginMode) "Email or handle" else "Password (6+ chars)") },
            singleLine = true,
            visualTransformation = if (loginMode || showPw) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                if (!loginMode) {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPw) "Hide password" else "Show password"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (loginMode) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPw) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = err.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "authError",
        ) {
            Text(err, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { if (loginMode) doLogin() else doSignup() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Please wait…" else if (loginMode) "Log in" else "Create account")
        }
        TextButton(onClick = { modeLogin = !modeLogin; err = "" }) {
            Text(if (loginMode) "Create account" else "Log in")
        }
        if (loginMode) {
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
        TextButton(onClick = { onAuthComplete(false) }) {
            Text("Continue as guest")
        }
        }
        }
    }
}
