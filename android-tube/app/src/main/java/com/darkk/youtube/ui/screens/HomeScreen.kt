package com.darkk.youtube.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.viewmodel.HomeUiState
import com.darkk.youtube.viewmodel.SearchUiState
import com.darkk.youtube.viewmodel.YouTubeViewModel

import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Notifications

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.composed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: YouTubeViewModel,
    innerPadding: PaddingValues,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
    onNotificationsClick: () -> Unit = {}
) {
    val homeState by viewModel.homeState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val showSearchSuggestions by viewModel.showSearchSuggestions.collectAsState()

    var localQuery by remember(searchQuery) { mutableStateOf(searchQuery) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val categories = listOf("All", "Gaming", "Music", "Live", "News", "Podcasts", "Mixes")
    var selectedCategory by remember { mutableStateOf("All") }
    val categoryFeedState by viewModel.getCategoryFeed(selectedCategory).collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    var selectedVideoForOptions by remember { mutableStateOf<VideoItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Ambient Background
        val firstVideoThumbnail = (homeState as? HomeUiState.Success)?.videos?.firstOrNull()?.thumbnail
        if (firstVideoThumbnail != null) {
            AsyncImage(
                model = firstVideoThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f) // Cover top part of the screen
                    .align(Alignment.TopCenter)
                    .blur(radius = 100.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.5f } // Glow effect
            )
            // Gradient to fade it out towards the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black, Color.Black),
                            startY = 0f,
                            endY = 1500f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // ── Top Bar ────────────────────────────────────────────────────────────
        Surface(
            color = Color.Black,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                com.darkk.youtube.ui.components.YouTubeTopBar(
                    viewModel = viewModel,
                    onNotificationsClick = onNotificationsClick
                )
                
                // Categories Row
                if (!isSearchActive) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it }) { category ->
                            val isSelected = category == selectedCategory
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color.White else Color(0xFF272727),
                                modifier = Modifier.clickable {
                                    selectedCategory = category
                                    viewModel.loadCategoryFeed(category)
                                }
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
            }
        }

        // ── Content ─────────────────────────────────────────────────────────
        if (isSearchActive) {
            if (showSearchSuggestions) {
                // Show suggestions or history
                SuggestionsPane(
                    suggestions = if (localQuery.isEmpty()) searchHistory else searchSuggestions,
                    isHistory = localQuery.isEmpty(),
                    onSuggestionClick = { suggestion ->
                        localQuery = suggestion
                        viewModel.search(suggestion)
                        keyboardController?.hide()
                    },
                    onPutInSearchBar = { suggestion ->
                        localQuery = suggestion
                        viewModel.updateSearchQuery(suggestion)
                    }
                )
            } else {
                SearchResultsPane(
                    state = searchState, 
                    onVideoClick = onVideoClick,
                    onLoadMore = { viewModel.loadMoreSearch() },
                    onChannelClick = onChannelClick,
                    onMoreClick = { selectedVideoForOptions = it }
                )
            }
        } else {
            // Home feed (category-aware)
            if (selectedCategory == "All") {
                HomeFeedPane(
                    state = homeState,
                    onVideoClick = onVideoClick,
                    onRetry = { viewModel.loadHomeFeed() },
                    onLoadMore = { viewModel.loadMoreHomeFeed() },
                    onChannelClick = onChannelClick,
                    onMoreClick = { selectedVideoForOptions = it }
                )
            } else {
                HomeFeedPane(
                    state = categoryFeedState,
                    onVideoClick = onVideoClick,
                    onRetry = { viewModel.loadCategoryFeed(selectedCategory) },
                    onLoadMore = { viewModel.loadMoreCategoryFeed(selectedCategory) },
                    onChannelClick = onChannelClick,
                    onMoreClick = { selectedVideoForOptions = it }
                )
            }
        }
        } // end Column
        
        selectedVideoForOptions?.let { video ->
            com.darkk.youtube.ui.components.VideoOptionsSheet(
                video = video,
                onDismissRequest = { selectedVideoForOptions = null },
                onPlayNext = {
                    viewModel.queueManager.playNext(video)
                    selectedVideoForOptions = null
                },
                onSaveToWatchLater = {
                    coroutineScope.launch {
                        viewModel.repository.toggleVideoInPlaylist("watch_later", video)
                    }
                    selectedVideoForOptions = null
                },
                onSaveToPlaylist = {
                    selectedVideoForOptions = null
                },
                onShare = { selectedVideoForOptions = null },
                onDownload = { isAudio, quality ->
                viewModel.downloadManager.startDownload(
                    com.darkk.youtube.download.DownloadInfo(
                        videoId = video.videoId,
                        title = video.title,
                        thumbnail = video.thumbnail,
                        channelName = video.channelName,
                        duration = video.duration,
                        quality = quality,
                        fileUri = ""
                    ))
                selectedVideoForOptions = null
            }
            )
        }
    }
}

@Composable
private fun HomeFeedPane(
    state: HomeUiState,
    onVideoClick: (VideoItem) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onChannelClick: (String) -> Unit,
    onMoreClick: (VideoItem) -> Unit
) {
    when (state) {
        is HomeUiState.Loading -> LoadingPane()
        is HomeUiState.Error -> ErrorPane(state.message, onRetry)
        is HomeUiState.Success -> VideoList(
            videos = state.videos,
            isFetchingMore = state.isFetchingMore,
            onVideoClick = onVideoClick,
            onLoadMore = onLoadMore,
            onChannelClick = onChannelClick,
            onMoreClick = onMoreClick
        )
    }
}

@Composable
private fun SuggestionsPane(
    suggestions: List<String>,
    isHistory: Boolean,
    onSuggestionClick: (String) -> Unit,
    onPutInSearchBar: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isHistory) Icons.Default.History else Icons.Default.Search, 
                    contentDescription = null, 
                    tint = Color.White, 
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(20.dp))
                Text(
                    text = suggestion, 
                    color = Color.White, 
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onPutInSearchBar(suggestion) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowOutward,
                        contentDescription = "Put in search bar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).rotate(270f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsPane(
    state: SearchUiState,
    onVideoClick: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onChannelClick: (String) -> Unit,
    onMoreClick: (VideoItem) -> Unit
) {
    when (state) {
        is SearchUiState.Idle -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type to search...", color = Color.Gray)
            }
        }
        is SearchUiState.Loading -> LoadingPane()
        is SearchUiState.Error -> ErrorPane(state.message, {})
        is SearchUiState.Success -> VideoList(
            videos = state.videos, 
            isFetchingMore = state.isFetchingMore,
            onVideoClick = onVideoClick,
            onLoadMore = onLoadMore,
            onChannelClick = onChannelClick,
            onMoreClick = onMoreClick
        )
    }
}

@Composable
private fun VideoList(
    videos: List<VideoItem>,
    isFetchingMore: Boolean = false,
    onVideoClick: (VideoItem) -> Unit,
    onLoadMore: () -> Unit = {},
    onChannelClick: (String) -> Unit,
    onMoreClick: (VideoItem) -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Group videos and shorts
    val groupedItems = remember(videos) {
        val groups = mutableListOf<Any>()
        var currentShorts = mutableListOf<VideoItem>()
        for (video in videos) {
            if (video.isShort) {
                currentShorts.add(video)
            } else {
                if (currentShorts.isNotEmpty()) {
                    groups.add(currentShorts)
                    currentShorts = mutableListOf()
                }
                groups.add(video)
            }
        }
        if (currentShorts.isNotEmpty()) {
            groups.add(currentShorts)
        }
        groups
    }

    LaunchedEffect(listState, groupedItems.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= groupedItems.size - 2) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 0.dp)
    ) {
        items(groupedItems.size, key = { index -> 
            val item = groupedItems[index]
            if (item is VideoItem) item.videoId else "shorts_${(item as List<*>).hashCode()}"
        }) { index ->
            val item = groupedItems[index]
            if (item is VideoItem) {
                VideoCard(video = item, onClick = { onVideoClick(item) }, onChannelAvatarClick = onChannelClick, onMoreClick = { onMoreClick(item) })
            } else if (item is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val shortsList = item as List<VideoItem>
                ShortsShelf(shorts = shortsList, onVideoClick = onVideoClick)
            }
        }
        
        if (isFetchingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ShortsShelf(shorts: List<VideoItem>, onVideoClick: (VideoItem) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_media_play),
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            val chunkedShorts = shorts.take(4).chunked(2)
            chunkedShorts.forEach { rowShorts ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (short in rowShorts) {
                        Box(modifier = Modifier.weight(1f)) {
                            ShortCard(video = short, onClick = { onVideoClick(short) })
                        }
                    }
                    if (rowShorts.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ShortCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Title overlay at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.viewCount,
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, onChannelAvatarClick: ((String) -> Unit)? = null, onMoreClick: (() -> Unit)? = null) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { onClick() }
            .padding(bottom = 0.dp) // No bottom padding for native feel, use a thin divider if needed
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            var thumbnailError by remember { mutableStateOf(false) }
            val thumbUrl = if (thumbnailError) {
                video.thumbnail.replace("hq720.jpg", "hqdefault.jpg").replace("maxresdefault.jpg", "hqdefault.jpg")
            } else {
                video.thumbnail.replace("hqdefault.jpg", "hq720.jpg")
            }
            
            AsyncImage(
                model = thumbUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { thumbnailError = true }
            )
            // Duration badge
            if (video.duration.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Video info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 24.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel avatar
            if (!video.channelAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = video.channelAvatar,
                    contentDescription = "Channel Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onChannelAvatarClick != null && video.channelUrl.isNotEmpty()) {
                            onChannelAvatarClick?.invoke(video.channelUrl)
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333))
                        .clickable(enabled = onChannelAvatarClick != null && video.channelUrl.isNotEmpty()) {
                            onChannelAvatarClick?.invoke(video.channelUrl)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = video.channelName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.channelName)
                        if (video.viewCount.isNotEmpty()) {
                            append(" • ")
                            append(video.viewCount)
                        }
                        if (video.publishedAt.isNotEmpty()) {
                            append(" • ")
                            append(video.publishedAt)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // More options
            IconButton(
                onClick = { onMoreClick?.invoke() },
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 8.dp, top = 2.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 0.dp)
        )
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition()
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF272727),
                Color(0xFF3C3C3C),
                Color(0xFF272727)
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

@Composable
fun VideoSkeletonCard() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shimmerEffect()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .shimmerEffect()
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
private fun LoadingPane() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(5) {
            VideoSkeletonCard()
        }
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Something went wrong",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}
