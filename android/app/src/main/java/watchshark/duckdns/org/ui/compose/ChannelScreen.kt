package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.ChannelUser
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.fmtNum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    username: String,
    onOpenVideo: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<ChannelUser?>(null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var meId by remember { mutableStateOf<Long?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(username) {
        loading = true
        try {
            meId = try {
                ApiClient.api.me().user?.id
            } catch (_: Exception) {
                null
            }
            val res = ApiClient.api.channel(username)
            user = res.user
            videos = res.videos.orEmpty()
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    if (loading && user == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val u = user ?: return
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ApiClient.fullUrl(u.avatar),
                contentDescription = u.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("@${u.username}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${fmtNum(u.followers)} followers • ${fmtNum(u.videos)} videos • ${fmtNum(u.views)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // No following yourself (same as web). Flips instantly (optimistic).
            if (meId != null && meId != u.id) {
                val following = u.following
                if (following) {
                    OutlinedButton(onClick = {
                        val cur = user ?: return@OutlinedButton
                        user = cur.copy(following = false, followers = (cur.followers - 1).coerceAtLeast(0))
                        scope.launch {
                            try {
                                ApiClient.api.follow(cur.id)
                            } catch (_: Exception) {
                                user = cur
                            }
                        }
                    }) { Text("Following") }
                } else {
                    Button(onClick = {
                        val cur = user ?: return@Button
                        user = cur.copy(following = true, followers = cur.followers + 1)
                        scope.launch {
                            try {
                                ApiClient.api.follow(cur.id)
                            } catch (_: Exception) {
                                user = cur
                            }
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_person_add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Follow")
                    }
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Videos") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Wheels") })
        }
        val shown = if (tab == 0) videos.filter { it.kind != "wheel" } else videos.filter { it.kind == "wheel" }
        AnimatedContent(targetState = tab, label = "channelTab") { _ ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
        ) {
            items(shown, key = { it.id }) { v ->
                VideoCard(video = v, onOpen = onOpenVideo, modifier = Modifier.animateItem())
            }
        }
        }
    }
}
