package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        SegmentedButtonDefaults.Icon(active = viewModel.sort == "new") {
                            Icon(Icons.Filled.NewReleases, contentDescription = null)
                        }
                    },
                    label = { Text("Latest") },
                )
                SegmentedButton(
                    selected = viewModel.sort == "popular",
                    onClick = { viewModel.setSort("popular") },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = viewModel.sort == "popular") {
                            Icon(Icons.Filled.Whatshot, contentDescription = null)
                        }
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.videos, key = { it.id }) { video ->
                    VideoCard(video = video, onOpen = onOpenVideo)
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
