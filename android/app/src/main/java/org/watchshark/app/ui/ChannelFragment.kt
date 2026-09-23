package org.watchshark.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient
import org.watchshark.app.data.ChannelUser
import org.watchshark.app.data.Video

class ChannelFragment : Fragment() {
    private var username = ""
    private var user = ChannelUser()
    private var allVideos = listOf<Video>()
    private var kind = "video"
    private lateinit var adapter: VideoAdapter

    companion object {
        fun newInstance(username: String) = ChannelFragment().apply {
            arguments = Bundle().apply { putString("user", username) }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        username = arguments?.getString("user", "") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_channel, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = VideoAdapter(mutableListOf())
        val grid: RecyclerView = view.findViewById(R.id.ch_grid)
        grid.layoutManager = GridLayoutManager(requireContext(), 2)
        grid.adapter = adapter
        view.findViewById<TabLayout>(R.id.ch_tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    kind = arrayOf("video", "wheel", "music")[tab.position]
                    renderGrid()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            },
        )
        view.findViewById<Button>(R.id.ch_follow).setOnClickListener { toggleFollow() }
        load()
    }

    private fun load() {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.channel(username)
                if (!isAdded) return@launch
                user = res.user
                allVideos = res.videos.orEmpty()
                v.findViewById<TextView>(R.id.ch_name).text = "@${user.username}"
                v.findViewById<TextView>(R.id.ch_stats).text =
                    "${fmtNum(user.followers)} followers • ${fmtNum(user.videos)} videos • ${fmtNum(user.views)} views"
                v.findViewById<ImageView>(R.id.ch_avatar).loadMedia(user.avatar, R.drawable.ic_person)
                val me = try {
                    ApiClient.api.me().user
                } catch (_: Exception) {
                    null
                }
                if (!isAdded) return@launch
                val follow: Button = v.findViewById(R.id.ch_follow)
                if (me != null && me.id != user.id) {
                    follow.visibility = View.VISIBLE
                    follow.text = if (user.following) "Following" else "Follow"
                }
                renderGrid()
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    private fun renderGrid() {
        adapter.setItems(allVideos.filter {
            val k = it.kind.ifEmpty { "video" }
            if (kind == "video") k == "video" else k == kind
        })
    }

    private fun toggleFollow() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.follow(user.id)
                if (!isAdded) return@launch
                user.following = res.get("following")?.asBoolean == true
                user.followers = res.get("followers")?.asLong ?: user.followers
                view?.findViewById<TextView>(R.id.ch_stats)?.text =
                    "${fmtNum(user.followers)} followers • ${fmtNum(user.videos)} videos • ${fmtNum(user.views)} views"
                view?.findViewById<Button>(R.id.ch_follow)?.text =
                    if (user.following) "Following" else "Follow"
            } catch (e: Exception) {
                if (isAdded) view?.snack(httpErrorMessage(e))
            }
        }
    }
}
