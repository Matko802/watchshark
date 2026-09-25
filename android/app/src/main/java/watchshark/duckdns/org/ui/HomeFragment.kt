package watchshark.duckdns.org.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    private lateinit var adapter: VideoAdapter
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
        grid.clearBottomBar()
        grid.adapter = adapter
        if (cached.isNotEmpty()) {
            adapter.setItems(cached)
            page = 1
            view.findViewById<Button>(R.id.more_btn).visibility =
                if (page < pages) View.VISIBLE else View.GONE
        }
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
        view.findViewById<Button>(R.id.more_btn).setOnClickListener { load(page + 1) }
        load(1)
    }
    private fun load(p: Int) {
        val v = view ?: return
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
                v.findViewById<Button>(R.id.more_btn).visibility =
                    if (p < pages) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (isAdded) v.snack(apiErrorMessage(e))
            }
        }
    }
}