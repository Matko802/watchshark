package org.watchshark.app.ui

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
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

/** Music as regular videos: same feed grid as home, square artwork cards. */
class MusicFragment : Fragment() {
    private var page = 1
    private var sort = "new"
    private var pages = 1
    private lateinit var adapter: VideoAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_music, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = VideoAdapter(mutableListOf(), {}, R.layout.item_video_square)
        val grid: RecyclerView = view.findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(requireContext(), gridSpan(requireContext()))
        grid.adapter = adapter

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
                    q = null,
                    sort = sort,
                    page = p.toLong(),
                    limit = 12,
                    kind = "music",
                )
                if (!isAdded) return@launch
                page = p
                pages = res.pages.toInt()
                val ready = res.videos.orEmpty().filter { it.status == "ready" }
                if (p == 1) adapter.setItems(ready) else adapter.append(ready)
                v.findViewById<Button>(R.id.more_btn).visibility =
                    if (p < pages) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (isAdded) v.snack(apiErrorMessage(e))
            }
        }
    }

    override fun onDestroyView() {
        // Release any avatar players held by visible holders.
        view?.findViewById<RecyclerView>(R.id.grid)?.let { grid ->
            for (i in 0 until grid.childCount) {
                grid.getChildViewHolder(grid.getChildAt(i))?.let { h ->
                    (h as? VideoAdapter.Holder)?.avatar?.release()
                }
            }
        }
        super.onDestroyView()
    }
}
