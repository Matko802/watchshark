package watchshark.duckdns.org.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Video

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val loading: Boolean = false,
    val page: Int = 1,
    val pages: Int = 1,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state

    var sort: String = "new"
        private set
    var query: String = ""
        private set
    private var queryJob: Job? = null

    init {
        refresh()
    }

    fun setSort(newSort: String) {
        if (sort == newSort) return
        sort = newSort
        refresh()
    }

    fun setQuery(q: String) {
        if (query == q) return
        query = q
        // Debounce typing: avoids a request per keystroke, keeps search smooth.
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(350)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val res = ApiClient.api.videos(
                    q = query.ifEmpty { null },
                    sort = sort,
                    page = 1,
                    limit = 24,
                    kind = "video",
                )
                _state.value = HomeUiState(
                    videos = res.videos.orEmpty(),
                    loading = false,
                    page = 1,
                    pages = res.pages.toInt(),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.page >= s.pages) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            try {
                val next = s.page + 1
                val res = ApiClient.api.videos(
                    q = query.ifEmpty { null },
                    sort = sort,
                    page = next.toLong(),
                    limit = 24,
                    kind = "video",
                )
                _state.value = s.copy(
                    videos = s.videos + res.videos.orEmpty(),
                    loading = false,
                    page = next,
                    pages = res.pages.toInt().coerceAtLeast(next),
                )
            } catch (e: Exception) {
                _state.value = s.copy(loading = false, error = e.message)
            }
        }
    }
}
