package com.darkk.youtube.viewmodel

import androidx.lifecycle.viewModelScope
import com.darkk.youtube.innertube.PlayerData
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.innertube.YouTubeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.darkk.youtube.innertube.SubscribedChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch


sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val videos: List<VideoItem>,
        val continuationToken: String? = null,
        val isFetchingMore: Boolean = false
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(
        val videos: List<VideoItem>,
        val continuationToken: String? = null,
        val isFetchingMore: Boolean = false
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class ChannelUiState {
    object Idle : ChannelUiState()
    object Loading : ChannelUiState()
    data class Success(val data: com.darkk.youtube.innertube.ChannelData) : ChannelUiState()
    data class Error(val message: String) : ChannelUiState()
}

sealed class SubscriptionsUiState {
    object Loading : SubscriptionsUiState()
    data class Success(val videos: List<VideoItem>) : SubscriptionsUiState()
    data class Error(val message: String) : SubscriptionsUiState()
}

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    object Loading : PlayerUiState()
    data class Ready(val data: PlayerData) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class YouTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)
    
    val downloadDatabase = com.darkk.youtube.download.DownloadDatabase(application)
    val downloadManager = com.darkk.youtube.download.DownloadManager(application, downloadDatabase)
    val queueManager = QueueManager()
    val repository = com.darkk.youtube.data.LocalRepository(application)
    
    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.loadData()
        }
    }
    
    private var currentVisitorData: String?
        get() = prefs.getString("visitorData", null)
        set(value) {
            prefs.edit().putString("visitorData", value).apply()
        }

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeState: StateFlow<HomeUiState> = _homeState

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState

    private val _channelState = MutableStateFlow<ChannelUiState>(ChannelUiState.Idle)
    val channelState: StateFlow<ChannelUiState> = _channelState

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive

    private val _showSearchSuggestions = MutableStateFlow(false)
    val showSearchSuggestions: StateFlow<Boolean> = _showSearchSuggestions

    fun setSearchSuggestionsVisible(visible: Boolean) {
        _showSearchSuggestions.value = visible
    }

    private val _playerState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val playerState: StateFlow<PlayerUiState> = _playerState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private val _watchHistory = MutableStateFlow<List<String>>(emptyList())
    val watchHistory: StateFlow<List<String>> = _watchHistory

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions

    private var suggestionJob: kotlinx.coroutines.Job? = null

    private val _subscriptions = MutableStateFlow<List<SubscribedChannel>>(emptyList())
    val subscriptions: StateFlow<List<SubscribedChannel>> = _subscriptions

    private val _subscriptionsFeedState = MutableStateFlow<SubscriptionsUiState>(SubscriptionsUiState.Loading)
    val subscriptionsFeedState: StateFlow<SubscriptionsUiState> = _subscriptionsFeedState

    private val _shortsList = MutableStateFlow<List<VideoItem>>(emptyList())
    val shortsList: StateFlow<List<VideoItem>> = _shortsList

    // Login state
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin

    fun checkLoginState() {
        _isLoggedIn.value = repository.isLoggedIn()
    }

    fun setShowLogin(show: Boolean) {
        _showLogin.value = show
    }

    // Category feeds: separate state per category tab
    private val _categoryFeeds = mutableMapOf<String, MutableStateFlow<HomeUiState>>()
    private val _currentCategory = MutableStateFlow("All")
    val currentCategory: StateFlow<String> = _currentCategory

    fun getCategoryFeed(category: String): StateFlow<HomeUiState> {
        return _categoryFeeds.getOrPut(category) {
            MutableStateFlow(HomeUiState.Loading)
        }
    }

    fun loadCategoryFeed(category: String) {
        _currentCategory.value = category
        if (category == "All") {
            loadHomeFeed()
            return
        }
        val feed = _categoryFeeds.getOrPut(category) { MutableStateFlow(HomeUiState.Loading) }
        feed.value = HomeUiState.Loading
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.search(query = category, visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    feed.value = HomeUiState.Success(
                        videos = page.videos,
                        continuationToken = page.continuationToken
                    )
                }
                .onFailure { e ->
                    feed.value = HomeUiState.Error(e.message ?: "Failed to load $category")
                }
        }
    }

    fun loadMoreCategoryFeed(category: String) {
        val feed = _categoryFeeds[category] ?: return
        val current = feed.value
        if (current !is HomeUiState.Success || current.continuationToken == null || current.isFetchingMore) return
        feed.value = current.copy(isFetchingMore = true)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.search(query = category, continuation = current.continuationToken, visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    val distinct = (current.videos + page.videos).distinctBy { it.videoId }
                    feed.value = HomeUiState.Success(
                        videos = distinct,
                        continuationToken = page.continuationToken,
                        isFetchingMore = false
                    )
                }
                .onFailure {
                    feed.value = current.copy(isFetchingMore = false)
                }
        }
    }

    // Preload cache for faster video start
    private val _playerDataCache = mutableMapOf<String, PlayerData>()
    private val _preloadingJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun preloadPlayerData(videoId: String) {
        if (_playerDataCache.containsKey(videoId) || _preloadingJobs.containsKey(videoId)) return
        _preloadingJobs[videoId] = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.getPlayerData(videoId)
                .onSuccess { data ->
                    _playerDataCache[videoId] = data
                }
            _preloadingJobs.remove(videoId)
        }
    }

    fun getCachedPlayerData(videoId: String): PlayerData? = _playerDataCache[videoId]


    init {
        try {
            val subsJson = prefs.getString("subscriptions", "[]") ?: "[]"
            _subscriptions.value = Json.decodeFromString(subsJson)
        } catch (e: Exception) {
            _subscriptions.value = emptyList()
        }
        _searchHistory.value = prefs.getString("search_history", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
        _watchHistory.value = prefs.getString("watch_history", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        
        viewModelScope.launch {
            downloadDatabase.load()
        }
        
        loadHomeFeed()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        suggestionJob?.cancel()
        _showSearchSuggestions.value = true
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(300) // debounce
            YouTubeApi.getSearchSuggestions(query)
                .onSuccess { suggestions ->
                    _searchSuggestions.value = suggestions
                }
                .onFailure {
                    _searchSuggestions.value = emptyList()
                }
        }
    }

    fun loadHomeFeed() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _homeState.value = HomeUiState.Loading
            YouTubeApi.getHomeFeed(visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    var videos = page.videos

                    if (videos.isNotEmpty() && _watchHistory.value.isNotEmpty()) {
                        val recentWatched = _watchHistory.value.take(3)
                        val personalizedVideos = mutableListOf<VideoItem>()
                        for (id in recentWatched) {
                            val related = YouTubeApi.getRelatedVideos(id).getOrNull() ?: emptyList()
                            personalizedVideos.addAll(related.filter { !it.isShort }.take(6))
                        }

                        if (personalizedVideos.isNotEmpty()) {
                            val dedupedPersonalized = personalizedVideos.distinctBy { it.videoId }
                            val merged = mutableListOf<VideoItem>()
                            var stdIndex = 0
                            var persIndex = 0
                            while (stdIndex < videos.size || persIndex < dedupedPersonalized.size) {
                                if (persIndex < dedupedPersonalized.size) merged.add(dedupedPersonalized[persIndex++])
                                if (persIndex < dedupedPersonalized.size) merged.add(dedupedPersonalized[persIndex++])
                                if (stdIndex < videos.size) merged.add(videos[stdIndex++])
                            }
                            videos = merged.distinctBy { it.videoId }
                        }
                    }

                    if (videos.isEmpty()) {
                        // Fallback: search for trending if home feed is empty
                        loadTrendingFallback()
                    } else {
                        _homeState.value = HomeUiState.Success(
                            videos = videos,
                            continuationToken = page.continuationToken
                        )
                    }
                }
                .onFailure { e ->
                    e.printStackTrace()
                    loadTrendingFallback()
                }
        }
    }

    fun loadMoreHomeFeed() {
        val currentState = _homeState.value
        if (currentState !is HomeUiState.Success || currentState.continuationToken == null || currentState.isFetchingMore) {
            return
        }

        _homeState.value = currentState.copy(isFetchingMore = true)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (currentState.continuationToken == "FALLBACK") {
                val queries = listOf("Gaming", "Music", "News", "Movies", "Comedy", "Vlogs", "Podcasts", "Technology", "Sports", "Nature")
                YouTubeApi.search(query = queries.random(), visitorData = currentVisitorData)
                    .onSuccess { page ->
                        val distinctVideos = (currentState.videos + page.videos).distinctBy { it.videoId }
                        _homeState.value = HomeUiState.Success(
                            videos = distinctVideos,
                            continuationToken = "FALLBACK",
                            isFetchingMore = false
                        )
                    }
                    .onFailure {
                        _homeState.value = currentState.copy(isFetchingMore = false)
                    }
            } else {
                YouTubeApi.getHomeFeed(continuation = currentState.continuationToken, visitorData = currentVisitorData)
                    .onSuccess { page ->
                        page.visitorData?.let { currentVisitorData = it }
                        val distinctVideos = (currentState.videos + page.videos).distinctBy { it.videoId }
                        
                        _homeState.value = HomeUiState.Success(
                            videos = distinctVideos,
                            continuationToken = page.continuationToken,
                            isFetchingMore = false
                        )
                    }
                    .onFailure { e ->
                        _homeState.value = currentState.copy(isFetchingMore = false)
                    }
            }
        }
    }

    private fun loadTrendingFallback() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val query = recommendationFallbackQuery()
            YouTubeApi.search(query = query, visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    _homeState.value = if (page.videos.isNotEmpty())
                        HomeUiState.Success(videos = page.videos, continuationToken = "FALLBACK")
                    else
                        HomeUiState.Error("No videos found")
                }
                .onFailure { e ->
                    e.printStackTrace()
                    _homeState.value = HomeUiState.Error(e.message ?: "Failed to load videos")
                }
        }
    }

    private fun recommendationFallbackQuery(): String {
        val recent = _watchHistory.value.firstOrNull()
        return when {
            recent != null -> "related music $recent"
            else -> "trending music"
        }
    }

    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        currentHistory.add(0, query)
        val newHistory = currentHistory.take(20)
        _searchHistory.value = newHistory
        prefs.edit().putString("search_history", newHistory.joinToString("||")).apply()
    }

    fun search(query: String) {
        _searchQuery.value = query
        _showSearchSuggestions.value = false
        if (query.isBlank()) {
            _searchState.value = SearchUiState.Idle
            return
        }
        saveSearchQuery(query)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _searchState.value = SearchUiState.Loading
            YouTubeApi.search(query = query, visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    _searchState.value = SearchUiState.Success(
                        videos = page.videos,
                        continuationToken = page.continuationToken
                    )
                }
                .onFailure { e ->
                    _searchState.value = SearchUiState.Error(e.message ?: "Search failed")
                }
        }
    }

    fun loadChannelData(channelUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _channelState.value = ChannelUiState.Loading
            YouTubeApi.getChannelDetails(channelUrl)
                .onSuccess { data ->
                    _channelState.value = ChannelUiState.Success(data)
                }
                .onFailure { e ->
                    _channelState.value = ChannelUiState.Error(e.message ?: "Failed to load channel")
                }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchUiState.Idle
        _searchQuery.value = ""
        _showSearchSuggestions.value = true
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        _showSearchSuggestions.value = active
    }

    fun loadMoreSearch() {
        val currentState = _searchState.value
        val currentQuery = _searchQuery.value
        if (currentState !is SearchUiState.Success || currentState.continuationToken == null || currentState.isFetchingMore) {
            return
        }
        _searchState.value = currentState.copy(isFetchingMore = true)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.search(query = currentQuery, continuation = currentState.continuationToken, visitorData = currentVisitorData)
                .onSuccess { page ->
                    page.visitorData?.let { currentVisitorData = it }
                    val distinctVideos = (currentState.videos + page.videos).distinctBy { it.videoId }
                    _searchState.value = SearchUiState.Success(
                        videos = distinctVideos,
                        continuationToken = page.continuationToken,
                        isFetchingMore = false
                    )
                }
                .onFailure {
                    _searchState.value = currentState.copy(isFetchingMore = false)
                }
        }
    }

    fun saveHistory(video: PlayerData) {
        if (!repository.canSaveHistory()) return
        val currentHistory = _watchHistory.value.toMutableList()
        currentHistory.remove(video.videoId)
        currentHistory.add(0, video.videoId)
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }
        _watchHistory.value = currentHistory
        prefs.edit().putString("watch_history", currentHistory.joinToString(",")).commit()
    }

    // --- Subscriptions Logic ---

    fun subscribe(channel: SubscribedChannel) {
        val current = _subscriptions.value.toMutableList()
        if (current.none { it.id == channel.id }) {
            current.add(0, channel) // Add to top
            _subscriptions.value = current
            saveSubscriptions()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.darkk.youtube.innertube.WatchSharkApi.followByName(channel.id.removePrefix("@"), true)
            } catch (_: Exception) {
            }
        }
    }

    fun unsubscribe(channelId: String) {
        val current = _subscriptions.value.toMutableList()
        current.removeAll { it.id == channelId }
        _subscriptions.value = current
        saveSubscriptions()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.darkk.youtube.innertube.WatchSharkApi.followByName(channelId.removePrefix("@"), false)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveSubscriptions() {
        try {
            val json = Json.encodeToString(_subscriptions.value)
            prefs.edit().putString("subscriptions", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSubscriptionsFeed() {
        val subs = _subscriptions.value
        if (subs.isEmpty()) {
            _subscriptionsFeedState.value = SubscriptionsUiState.Success(emptyList())
            return
        }
        _subscriptionsFeedState.value = SubscriptionsUiState.Loading
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fetch latest videos from up to 5 recently updated channels for performance
                // In a real scenario we'd query all or use a proper feed endpoint.
                val topChannels = subs.take(5)
                val allVideos = mutableListOf<VideoItem>()
                
                val deferreds = topChannels.map { channel ->
                    async {
                        val result = YouTubeApi.getChannelDetails(channel.id)
                        result.getOrNull()?.videos?.take(5) ?: emptyList()
                    }
                }
                
                deferreds.awaitAll().forEach { videos ->
                    allVideos.addAll(videos)
                }
                
                // Note: sorting by date requires parsing publishedAt, which is mostly "2 days ago", etc.
                // We will shuffle or just keep them grouped. Interleaving is better.
                _subscriptionsFeedState.value = SubscriptionsUiState.Success(allVideos.shuffled())
            } catch (e: Exception) {
                _subscriptionsFeedState.value = SubscriptionsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadPlayerData(videoId: String) {
        if (repository.canSaveHistory()) {
            val currentHistory = _watchHistory.value.toMutableList()
            currentHistory.remove(videoId)
            currentHistory.add(0, videoId)
            if (currentHistory.size > 100) currentHistory.removeLast()
            _watchHistory.value = currentHistory
            prefs.edit().putString("watch_history", currentHistory.joinToString(",")).commit()
        }

        // Check cache first for instant playback
        val cached = _playerDataCache[videoId]
        if (cached != null) {
            _playerState.value = PlayerUiState.Ready(cached)
            _playerDataCache.remove(videoId)
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (cached == null) {
                _playerState.value = PlayerUiState.Loading
            }
            
            // Fire and forget playback tracking
            launch {
                YouTubeApi.pingPlayback(videoId, currentVisitorData)
            }
            
            YouTubeApi.getPlayerData(videoId)
                .onSuccess { data ->
                    _playerState.value = PlayerUiState.Ready(data)
                    // Preload next likely video (first related)
                    launch {
                        val related = YouTubeApi.getRelatedVideos(videoId).getOrNull().orEmpty()
                        related.firstOrNull()?.let { preloadPlayerData(it.videoId) }
                    }
                }
                .onFailure { e ->
                    if (cached == null) {
                        _playerState.value = PlayerUiState.Error(e.message ?: "Failed to load video")
                    }
                }
        }
    }

    var isLoadingMoreComments = false
        private set

    fun loadMoreComments() {
        val currentState = _playerState.value as? PlayerUiState.Ready ?: return
        val page = currentState.data.nextCommentsPage ?: return
        if (isLoadingMoreComments) return
        
        isLoadingMoreComments = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.getMoreComments(currentState.data.videoId, page)
                .onSuccess { (newComments, nextPage) ->
                    val newData = currentState.data.copy(
                        comments = currentState.data.comments + newComments,
                        nextCommentsPage = nextPage
                    )
                    _playerState.value = PlayerUiState.Ready(newData)
                    isLoadingMoreComments = false
                }
                .onFailure {
                    isLoadingMoreComments = false
                }
        }
    }

    fun resetPlayer() {
        _playerState.value = PlayerUiState.Idle
    }

    fun loadShortsFeed() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            YouTubeApi.search("shorts")
                .onSuccess { page ->
                    val filtered = page.videos.filter { 
                        it.isShort || it.duration.isEmpty() || isShortDuration(it.duration)
                    }
                    _shortsList.value = filtered.distinctBy { it.videoId }
                }
        }
    }

    private fun isShortDuration(duration: String): Boolean {
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return false
        }
        return seconds in 1..60
    }
}
