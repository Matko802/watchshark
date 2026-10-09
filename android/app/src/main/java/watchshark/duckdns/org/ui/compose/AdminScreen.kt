package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.AdminUser
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.ui.fmtDateTime
import watchshark.duckdns.org.ui.httpErrorMessage

@Composable
fun AdminScreen(
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                users = ApiClient.api.adminUsers().users.orEmpty()
                error = null
            } catch (e: Exception) {
                error = httpErrorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Admin", style = MaterialTheme.typography.headlineSmall)
        if (loading && users.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 104.dp),
        ) {
            items(users, key = { it.id }) { u ->
                AdminRow(
                    user = u,
                    onOpenChannel = onOpenChannel,
                    onChanged = { reload() },
                    onError = { error = it },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun AdminRow(
    user: AdminUser,
    onOpenChannel: (String) -> Unit,
    onChanged: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val isAdmin = user.role == "admin"
    val status = when {
        user.deleted -> "deleted"
        user.banned -> "banned"
        user.verified -> "active"
        else -> "pending"
    }
    var extra = ""
    if (user.deleted && user.deleted_reason.isNotEmpty()) extra = " • " + user.deleted_reason
    if (user.banned) {
        val dl = if (user.ban_days_left < 0) "permanent" else "%.1fd left".format(user.ban_days_left)
        extra = " • $dl" + if (user.ban_reason.isNotEmpty()) " • ${user.ban_reason}" else ""
    }

    fun run(call: suspend () -> Unit) {
        scope.launch {
            try {
                call()
                onChanged()
            } catch (e: Exception) {
                onError(httpErrorMessage(e))
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { onOpenChannel(user.username) },
            ) {
                AsyncImage(
                    model = ApiClient.fullUrl(user.avatar),
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
                Column {
                    Text(
                        user.username + if (isAdmin) " ⭐" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "$status • ${fmtDateTime(user.created_at)}$extra",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isAdmin) {
                if (!user.deleted && !user.banned) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = days,
                            onValueChange = { days = it },
                            label = { Text("Days") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                    }
                    Button(onClick = {
                        val d = days.toDoubleOrNull() ?: 0.0
                        if (d <= 0) {
                            onError("Give ban days")
                            return@Button
                        }
                        run {
                            ApiClient.api.adminBan(
                                mapOf(
                                    "id" to user.id,
                                    "days" to d,
                                    "hours" to 0.0,
                                    "reason" to reason,
                                ),
                            )
                        }
                    }) { Text("Ban") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!user.verified && !user.deleted) {
                        OutlinedButton(onClick = {
                            run { ApiClient.api.adminApprove(mapOf("id" to user.id)) }
                        }) { Text("Approve") }
                    }
                    if (user.banned && !user.deleted) {
                        OutlinedButton(onClick = {
                            run { ApiClient.api.adminUnban(mapOf("id" to user.id)) }
                        }) { Text("Unban") }
                    }
                    if (user.deleted) {
                        OutlinedButton(onClick = {
                            run { ApiClient.api.adminRestore(mapOf("id" to user.id)) }
                        }) { Text("Restore") }
                    }
                    if (!user.deleted) {
                        OutlinedButton(onClick = {
                            run {
                                ApiClient.api.adminSoftDelete(
                                    mapOf("id" to user.id, "reason" to "Removed by admin"),
                                )
                            }
                        }) { Text("Delete") }
                    }
                    OutlinedButton(onClick = {
                        run { ApiClient.api.adminDelUser(user.id) }
                    }) { Text("Remove") }
                }
            }
        }
    }
}
