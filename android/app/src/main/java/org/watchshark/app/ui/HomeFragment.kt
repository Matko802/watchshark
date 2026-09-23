package org.watchshark.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

class HomeFragment : Fragment() {
    private var page = 1
    private var sort = "new"
    private var query = ""
    private var pages = 1
    private lateinit var adapter: VideoAdapter
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = VideoAdapter(mutableListOf())
        val grid: RecyclerView = view.findViewById(R.id.grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 2)
        grid.adapter = adapter

        val search: TextInputEditText = view.findViewById(R.id.search)
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                query = search.text.toString()
                load(1)
                true
            } else false
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    query = s.toString()
                    load(1)
                }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
        })

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
                v.findViewById<TextView>(R.id.count).text = "${res.total} videos"
                if (p == 1) adapter.setItems(res.videos) else adapter.append(res.videos)
                v.findViewById<Button>(R.id.more_btn).visibility =
                    if (p < pages) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                if (isAdded) v.snack(apiErrorMessage(e))
            }
        }
    }
}
