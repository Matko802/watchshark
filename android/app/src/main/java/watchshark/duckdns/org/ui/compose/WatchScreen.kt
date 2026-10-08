package watchshark.duckdns.org.ui.compose

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.fmtAge
import watchshark.duckdns.org.ui.fmtNum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    videoId: Long,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var video by remember { mutableStateOf<Video?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        loading = true
        error = null
        try {
            val res = ApiClient.api.videoDetail(videoId)
            video = res.video
            comments = res.comments.orEmpty()
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        } finally {
            loading = false
        }
    }

    // ExoPlayer with session cookie (server requires auth for streams).
    val player = remember(videoId) { ApiClient.buildPlayer(context) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(video) {
        val v = video ?: return@LaunchedEffect
        val url = v.renditions?.get("720p")
            ?: v.renditions?.values?.firstOrNull()
            ?: ApiClient.fullUrl(v.src)
            ?: return@LaunchedEffect
        try {
            player.setMediaItem(ApiClient.mediaItem(url))
            player.prepare()
            player.playWhenReady = true
        } catch (_: Exception) {
        }
    }

    if (loading && video == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val v = video
    if (v == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error ?: "Video not found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        useController = true
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(v.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${fmtNum(v.views)} views • ${fmtAge(v.created_at)} • ${fmtNum(v.likes)} likes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ApiClient.fullUrl(v.avatar),
                        contentDescription = v.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(CircleShape),
                    )
                    TextButton(onClick = { onOpenChannel(v.username) }) {
                        Text("@${v.username}")
                    }
                    // Expressive button group: Follow (outlined) + Like (tonal chip).
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    ApiClient.api.follow(v.userId)
                                    video = v.copy(
                                        following = !v.following,
                                        followers = v.followers + if (v.following) -1 else 1,
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        },
                    ) {
                        Text(if (v.following) "Following" else "Follow")
                    }
                    FilterChip(
                        selected = v.liked,
                        onClick = {
                            scope.launch {
                                try {
                                    ApiClient.api.like(v.id)
                                    video = v.copy(
                                        liked = !v.liked,
                                        likes = v.likes + if (v.liked) -1 else 1,
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        },
                        label = { Text("${fmtNum(v.likes)}") },
                        leadingIcon = {
                            Icon(Icons.Filled.ThumbUp, contentDescription = null)
                        },
                    )
                }
                if (!v.description.isNullOrEmpty()) {
                    Text(
                        v.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .padding(top = 4.dp),
                    )
                }
                Text(
                    "Comments (${comments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(comments, key = { it.id }) { c ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (c.avatar != null) {
                    AsyncImage(
                        model = ApiClient.fullUrl(c.avatar),
                        contentDescription = c.username,
                        modifier = Modifier.clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
                Column {
                    Text(
                        "@${c.username} • ${fmtAge(c.created_at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
