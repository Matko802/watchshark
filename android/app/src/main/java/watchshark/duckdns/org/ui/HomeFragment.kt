package watchshark.duckdns.org.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
class HomeFragment : Fragment() {
    private var page = 1
    private var sort = "new"
    private var query = ""
    private var pages = 1
    private var loading = false
    private lateinit var adapter: VideoAdapter
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private val cached = mutableListOf<watchshark.duckdns.org.data.Video>()
    /** Driven by the topbar search input. */
    fun setQuery(q: String) {
        if (query == q) return
        query = q
        load(1)
    }
    fun currentQuery(): String = query
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = VideoAdapter(mutableListOf())
        val grid: RecyclerView = view.findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(requireContext(), gridSpan(requireContext()))
        grid.clearBottomBar(clip = false)
        grid.adapter = adapter
        if (cached.isNotEmpty()) {
            adapter.setItems(cached)
            page = 1
        }
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as? GridLayoutManager ?: return
                val last = lm.findLastVisibleItemPosition()
                if (last >= adapter.itemCount - 4 && !loading && page < pages) {
                    load(page + 1)
                }
            }
        }
        grid.addOnScrollListener(scrollListener!!)
        view.findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    sort = if (tab.position == 1) "popular" else "new"
                    load(1)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            },
        )
        load(1)
    }
    override fun onDestroyView() {
        scrollListener?.let { view?.findViewById<RecyclerView>(R.id.grid)?.removeOnScrollListener(it) }
        scrollListener = null
        super.onDestroyView()
    }
    private fun load(p: Int) {
        val v = view ?: return
        if (loading) return
        loading = true
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.videos(
                    q = query.ifEmpty { null },
                    sort = sort,
                    page = p.toLong(),
                    limit = 12,
                    kind = "video",
                )
                if (!isAdded) return@launch
                page = p
                pages = res.pages.toInt()
                val ready = res.videos.orEmpty()
                if (p == 1) {
                    cached.clear()
                    cached.addAll(ready)
                    adapter.setItems(ready)
                } else adapter.append(ready)
            } catch (e: Exception) {
                if (isAdded) v.snack(apiErrorMessage(e))
            } finally {
                loading = false
            }
        }
    }
}