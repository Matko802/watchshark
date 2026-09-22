package org.watchshark.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient
import org.watchshark.app.data.Video

class MusicFragment : Fragment() {
    private val tracks = mutableListOf<Video>()
    private var sort = "new"
    private var ti = -1
    private var player: ExoPlayer? = null
    private lateinit var adapter: TrackAdapter
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            syncProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_music, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        adapter = TrackAdapter(tracks) { i -> if (i == ti) toggle() else playTrack(i) }
        view.findViewById<RecyclerView>(R.id.tracklist).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MusicFragment.adapter
        }
        player = ExoPlayer.Builder(requireContext()).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncPlayIcons()
                    adapter.notifyDataSetChanged()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) playTrack((ti + 1) % tracks.size)
                }
            })
        }
        view.findViewById<TabLayout>(R.id.music_tabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    sort = if (tab.position == 1) "pop" else "new"
                    load()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            },
        )
        view.findViewById<View>(R.id.mini_play).setOnClickListener { toggle() }
        view.findViewById<View>(R.id.mini_next).setOnClickListener { playTrack((ti + 1) % tracks.size) }
        view.findViewById<View>(R.id.mini_prev).setOnClickListener {
            playTrack((ti - 1 + tracks.size) % tracks.size)
        }
        view.findViewById<View>(R.id.mini_meta).setOnClickListener { setFullVisible(true) }
        view.findViewById<View>(R.id.full_play).setOnClickListener { toggle() }
        view.findViewById<View>(R.id.full_next).setOnClickListener { playTrack((ti + 1) % tracks.size) }
        view.findViewById<View>(R.id.full_prev).setOnClickListener {
            playTrack((ti - 1 + tracks.size) % tracks.size)
        }
        view.findViewById<View>(R.id.full_close).setOnClickListener { setFullVisible(false) }
        view.findViewById<Slider>(R.id.seek).addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player?.let { if (it.duration > 0) it.seekTo((value / 1000f * it.duration).toLong()) }
            }
        }
        progressHandler.post(progressTick)
        load()
    }

    private fun setFullVisible(visible: Boolean) {
        view?.findViewById<View>(R.id.full_player)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.videos(
                    sort = if (sort == "pop") "popular" else "new",
                    page = 1, limit = 48, kind = "music",
                )
                if (!isAdded) return@launch
                tracks.clear()
                tracks.addAll(res.videos.filter { it.status == "ready" })
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                if (isAdded) view?.snack(httpErrorMessage(e))
            }
        }
    }

    private fun playTrack(i: Int) {
        if (tracks.isEmpty()) return
        ti = (i + tracks.size) % tracks.size
        val t = tracks[ti]
        val url = fullUrl(t.src) ?: return
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
        showTrack(t)
    }

    private fun toggle() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun showTrack(t: Video) {
        val v = view ?: return
        v.findViewById<View>(R.id.mini_bar).visibility = View.VISIBLE
        v.findViewById<TextView>(R.id.mini_title).text = t.title
        v.findViewById<TextView>(R.id.mini_artist).text = "@${t.username}"
        v.findViewById<ImageView>(R.id.mini_thumb).loadMedia(t.thumbnail, R.drawable.ic_music_note)
        v.findViewById<TextView>(R.id.full_title).text = t.title
        v.findViewById<TextView>(R.id.full_artist).text = "@${t.username}"
        v.findViewById<ImageView>(R.id.full_art).loadMedia(t.thumbnail, R.drawable.ic_music_note)
        syncPlayIcons()
        adapter.notifyDataSetChanged()
    }

    private fun syncPlayIcons() {
        val playing = player?.isPlaying == true
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
        view?.findViewById<ImageButton>(R.id.mini_play)?.setImageResource(icon)
        view?.findViewById<ImageButton>(R.id.full_play)?.setImageResource(icon)
        adapter.playingIdx = ti
        adapter.isPlaying = playing
        adapter.notifyDataSetChanged()
    }

    private fun syncProgress() {
        val v = view ?: return
        val p = player ?: return
        val d = p.duration.coerceAtLeast(0)
        val c = p.currentPosition.coerceAtLeast(0)
        if (d > 0) v.findViewById<Slider>(R.id.seek).value = (c.toFloat() / d * 1000)
        v.findViewById<TextView>(R.id.time_cur).text = fmtDur(c / 1000)
        v.findViewById<TextView>(R.id.time_dur).text = fmtDur(d / 1000)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        progressHandler.removeCallbacks(progressTick)
    }

    override fun onResume() {
        super.onResume()
        progressHandler.post(progressTick)
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressTick)
        player?.release()
        player = null
        super.onDestroyView()
    }

    class TrackAdapter(
        private val items: List<Video>,
        private val onTap: (Int) -> Unit,
    ) : RecyclerView.Adapter<TrackAdapter.Holder>() {
        var playingIdx = -1
        var isPlaying = false

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.t_thumb)
            val title: TextView = v.findViewById(R.id.t_title)
            val artist: TextView = v.findViewById(R.id.t_artist)
            val plays: TextView = v.findViewById(R.id.t_plays)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val t = items[position]
            h.thumb.loadMedia(t.thumbnail, R.drawable.ic_music_note)
            h.title.text = t.title
            h.artist.text = "@${t.username}"
            h.plays.text = fmtNum(t.views)
            h.title.setTextColor(
                if (position == playingIdx && isPlaying) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt(),
            )
            h.itemView.alpha = if (position == playingIdx) 1.0f else 0.85f
            h.itemView.setOnClickListener { onTap(h.bindingAdapterPosition) }
        }
    }
}
