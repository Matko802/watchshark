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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.ChannelUser
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.fmtNum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    username: String,
    onOpenVideo: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<ChannelUser?>(null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var meId by remember { mutableStateOf<Long?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    suspend fun load() {
        loading = true
        error = null
        try {
            meId = try {
                ApiClient.api.me().user?.id
            } catch (_: Exception) {
                null
            }
            val res = ApiClient.api.channel(username)
            user = res.user
            videos = res.videos.orEmpty()
        } catch (e: Exception) {
            error = watchshark.duckdns.org.data.AppErrors.message(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(username) {
        load()
    }

    if (loading && user == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (user == null) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Could not load channel", style = MaterialTheme.typography.titleMedium)
            Text(error ?: "", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { scope.launch { load() } }) { Text("Retry") }
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

            if (meId != null && meId != u.id) {
                val following = u.following
                if (following) {
                    OutlinedButton(onClick = {
                        val cur = user ?: return@OutlinedButton
                        Haptics.tick(context)
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
                        Haptics.tick(context)
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
                            contentDescription = "Follow ${u.username}",
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Follow")
                    }
                }
            }
        }
        val shown = when (tab) {
            0 -> videos.filter { it.kind != "wheel" && it.kind != "music" }
            1 -> videos.filter { it.kind == "wheel" }
            else -> videos.filter { it.kind == "music" }
        }
        val gridState = rememberLazyGridState()
        LaunchedEffect(tab) {
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {
            }
        }
        AnimatedContent(targetState = tab, label = "channelTab") { _ ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 104.dp),
        ) {
            stickyHeader {
                SectionTabs(
                    tabs = listOf(
                        SectionTab("Videos", iconRes = R.drawable.ic_play),
                        SectionTab("Wheels", iconRes = R.drawable.ic_movie),
                        SectionTab("Music", iconRes = R.drawable.ic_music_note),
                    ),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }
            items(shown, key = { it.id }) { v ->
                VideoCard(video = v, onOpen = onOpenVideo, modifier = Modifier.animateItem())
            }
        }
        }
    }
}
