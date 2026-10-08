package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import watchshark.duckdns.org.data.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenVideo: (Video) -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
    query: String = "",
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(query) {
        viewModel.setQuery(query)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Floating slider pill like on web: Latest / Trending segmented control.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = viewModel.sort == "new",
                    onClick = { viewModel.setSort("new") },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {
                        Icon(Icons.Filled.NewReleases, contentDescription = null)
                    },
                    label = { Text("Latest") },
                )
                SegmentedButton(
                    selected = viewModel.sort == "popular",
                    onClick = { viewModel.setSort("popular") },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {
                        Icon(Icons.Filled.Whatshot, contentDescription = null)
                    },
                    label = { Text("Trending") },
                )
            }
        }

        val gridState = rememberLazyGridState()
        // Endless pagination: load more when near the end.
        LaunchedEffect(gridState) {
            snapshotFlow {
                val layout = gridState.layoutInfo
                val total = layout.totalItemsCount
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                Pair(total, last)
            }.map { (total, last) -> last >= total - 6 && total > 0 }
                .distinctUntilChanged()
                .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
        }

        if (state.videos.isEmpty() && state.loading) {
            // Shimmer skeleton grid instead of a bare spinner.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false,
            ) {
                items(6) { VideoCardSkeleton() }
            }
        } else if (state.videos.isEmpty() && state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("Couldn't load videos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.error ?: "Network error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
        } else if (state.videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No videos yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (query.isNotEmpty()) "Try a different search"
                        else "Pull down to refresh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.refresh() },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
            AnimatedContent(
                targetState = viewModel.sort,
                label = "sortSwitch",
            ) { _ ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        onOpen = onOpenVideo,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.loading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            }
            }
        }
    }
}

@Composable
private fun VideoCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    val shimmer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmer),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(shimmer)
                    .padding(18.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).padding(top = 2.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmer)
                        .padding(8.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmer)
                        .padding(6.dp),
                )
            }
        }
    }
}
