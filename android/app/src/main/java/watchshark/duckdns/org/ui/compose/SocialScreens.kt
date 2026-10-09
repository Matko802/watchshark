package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AppErrors
import watchshark.duckdns.org.data.DmConversation
import watchshark.duckdns.org.data.DmMessage
import watchshark.duckdns.org.ui.fmtAge

@Composable
fun MessagesScreen(
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var conversations by remember { mutableStateOf<List<DmConversation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        loading = true
        error = null
        try {
            conversations = ApiClient.api.dmConversations().conversations.orEmpty()
        } catch (e: Exception) {
            AppErrors.log(e, "messages")
            error = AppErrors.message(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) {
        load()
        while (isActive) {
            delay(15000)
            try {
                conversations = ApiClient.api.dmConversations().conversations.orEmpty()
            } catch (_: Exception) {
            }
        }
    }
    if (loading && conversations.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null && conversations.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Could not load messages", style = MaterialTheme.typography.titleMedium)
            Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { scope.launch { load() } }) { Text("Retry") }
        }
        return
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { scope.launch { load() } },
        state = pullState,
        modifier = modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        items(conversations, key = { it.userId }) { c ->
            ListItem(
                headlineContent = { Text("@${c.username}") },
                supportingContent = { Text(c.lastMessage, maxLines = 1) },                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            fmtAge(c.lastAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (c.unread > 0) {
                            Text(
                                if (c.unread > 99) "99+" else "${c.unread}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
                leadingContent = {
                    if (c.avatar != null) {
                        AsyncImage(
                            model = ApiClient.fullUrl(c.avatar),
                            contentDescription = c.username,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = c.username)
                    }
                },
                modifier = Modifier
                    .clickable { onOpenThread(c.username) }
                    .animateItem(),
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    username: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    var meId by remember { mutableStateOf(0L) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            val res = ApiClient.api.dmThread(username, null, null, 50)
            messages = res.messages.orEmpty()
            meId = res.me
            val lastId = messages.lastOrNull()?.id
            if (lastId != null) {
                try {
                    ApiClient.api.dmRead(mapOf("user" to username, "after_id" to lastId))
                } catch (e: Exception) {
                    AppErrors.log(e, "dmRead")
                }
            }
        } catch (e: Exception) {
            AppErrors.log(e, "dmThread")
        } finally {
            loading = false
        }
    }
    LaunchedEffect(username) {
        loading = true
        reload()
        while (isActive) {
            delay(10000)
            try {
                val res = ApiClient.api.dmThread(username, null, null, 50)
                messages = res.messages.orEmpty()
                meId = res.me
            } catch (_: Exception) {
            }
        }
    }
    Column(modifier.fillMaxSize().imePadding()) {
        if (loading && messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
            ) {
                items(messages.reversed(), key = { it.id }) { m ->
                    val mine = m.senderId == meId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .animateItem(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    ) {
                        Text(
                            m.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (mine) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .background(
                                    color = if (mine) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; sendError = null },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message @$username") },
                singleLine = false,
                maxLines = 4,
            )
            IconButton(onClick = {
                val body = draft.trim()
                if (body.isEmpty() || sending) return@IconButton
                draft = ""
                sendError = null
                sending = true
                scope.launch {
                    try {
                        ApiClient.api.dmSend(mapOf("to" to username, "user" to username, "body" to body))
                        reload()
                    } catch (e: Exception) {
                        AppErrors.log(e, "dmSend")
                        sendError = AppErrors.message(e)
                        draft = body
                    } finally {
                        sending = false
                    }
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message to $username")
            }
        }
        if (sendError != null) {
            Text(
                sendError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun NotificationsScreen(
    onOpenVideo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(emptyList<watchshark.duckdns.org.data.Notif>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        loading = true
        error = null
        try {
            items = ApiClient.api.notifications().notifications.orEmpty()
        } catch (e: Exception) {
            AppErrors.log(e, "notifications")
            error = AppErrors.message(e)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    if (loading && items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null && items.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Could not load notifications", style = MaterialTheme.typography.titleMedium)
            Text(error ?: "", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { scope.launch { load() } }) { Text("Retry") }
        }
        return
    }
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("All caught up", style = MaterialTheme.typography.titleMedium)
                Text(
                    "New likes, follows and uploads land here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { scope.launch { load() } }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Refresh")
                }
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = {
                items = items.map { it.copy(read = true) }
                scope.launch {
                    try {
                        ApiClient.api.notifRead(mapOf("id" to null))
                    } catch (e: Exception) {
                        AppErrors.log(e, "notifReadAll")
                    }
                }
            }) { Text("Mark all read") }
        }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items.size, key = { items[it].id }) { index ->
            val n = items[index]
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
                shape = shape,
                color = if (!n.read) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.animateItem(),
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            n.title,
                            color = if (!n.read) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = { Text("@${n.username} • ${fmtAge(n.created_at)}") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(
                                    if (n.videoId != null) R.drawable.ic_play
                                    else R.drawable.ic_notifications,
                                ),
                                contentDescription = n.title,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    },
                    trailingContent = {
                        if (!n.read) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Open notification",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            items = items.map {
                                if (it.id == n.id) it.copy(read = true) else it
                            }
                            scope.launch {
                                try {
                                    ApiClient.api.notifRead(mapOf("id" to n.id))
                                } catch (e: Exception) {
                                    AppErrors.log(e, "notifRead")
                                }
                            }
                            n.videoId?.let { onOpenVideo(it) }
                        },
                    ),
                )
            }
        }
    }
    }
}
