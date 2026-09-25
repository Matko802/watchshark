package watchshark.duckdns.org.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.Video
class WheelsFragment : Fragment() {
    private var player: ExoPlayer? = null
    private val videos = mutableListOf<Video>()
    private val seen = mutableListOf<Long>()
    private lateinit var adapter: ReelAdapter
    private var loading = false
    private var exhausted = false
    private var muted = false
    private var selectedPos = 0
    private var prepared = false
    private val qualityOverride = mutableMapOf<Long, String>()
    /** Rung currently playing per reel (for adaptive switches). */
    private val autoKeys = mutableMapOf<Long, String>()
    /** True once the current item rendered a frame (initial buffering never downgrades). */
    private var wheelReady = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_wheels, container, false)
    }
    override fun onViewCreated(view: View, saved: Bundle?) {
        loadSeen()
        player = ApiClient.buildPlayer(requireContext()).also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    wheelReady = false
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val back = (exo.currentMediaItemIndex - 1).coerceAtLeast(0)
                        exo.seekToDefaultPosition(back)
                        exo.pause()
                        exo.playWhenReady = false
                        view.findViewById<ViewPager2>(R.id.pager).setCurrentItem(back, false)
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        exo.pause()
                    } else if (state == Player.STATE_READY) {
                        wheelReady = true
                    } else if (state == Player.STATE_BUFFERING && exo.playWhenReady && wheelReady) {
                        autoStepDownCurrent(exo)
                    }
                }
            })
        }
        adapter = ReelAdapter()
        val pager: ViewPager2 = view.findViewById(R.id.pager)
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val old = selectedPos
                selectedPos = position
                if (old != position) {
                    adapter.notifyItemChanged(old)
                    adapter.notifyItemChanged(position)
                }
                player?.let { exo ->
                    if (exo.currentMediaItemIndex != position && position < exo.mediaItemCount) {
                        exo.seekTo(position, 0)
                    }
                    exo.playWhenReady = true
                    autoUpgradeCurrent(exo, position)
                }
                if (position >= videos.size - 3) loadMore()
            }
        })
        loadMore()
    }
    /** Faster internet than the current reel's rung: swap it up in place. */
    private fun autoUpgradeCurrent(exo: ExoPlayer, position: Int) {
        val vid = videos.getOrNull(position) ?: return
        if (qualityOverride.containsKey(vid.id)) return
        if (position >= exo.mediaItemCount) return
        val want = AutoQuality.pickKey()
        if (AutoQuality.rungIndex(want) <= AutoQuality.rungIndex(autoKeys[vid.id])) return
        val url = fullUrl(AutoQuality.urlFor(vid, want) ?: vid.src) ?: return
        if (!AutoQuality.tryBeginSwitch(AutoQuality.UPGRADE_GAP_MS)) return
        autoKeys[vid.id] = want
        val time = exo.currentPosition.coerceAtLeast(0)
        val playing = exo.isPlaying
        exo.removeMediaItem(position)
        exo.addMediaItem(position, MediaItem.fromUri(url))
        exo.seekTo(position, time)
        if (playing) exo.play()
    }
    /** Stall mid-reel: one rung down in place (unless manual override). */
    private fun autoStepDownCurrent(exo: ExoPlayer) {
        val pos = exo.currentMediaItemIndex
        val vid = videos.getOrNull(pos) ?: return
        if (qualityOverride.containsKey(vid.id)) return
        if (pos >= exo.mediaItemCount) return
        val idx = AutoQuality.rungIndex(autoKeys[vid.id])
        if (idx <= 0) return
        val want = AutoQuality.rungKey(idx - 1)
        val url = fullUrl(AutoQuality.urlFor(vid, want) ?: vid.src) ?: return
        if (!AutoQuality.tryBeginSwitch(AutoQuality.DOWNGRADE_GAP_MS)) return
        autoKeys[vid.id] = want
        wheelReady = false
        val time = exo.currentPosition.coerceAtLeast(0)
        val playing = exo.isPlaying
        exo.removeMediaItem(pos)
        exo.addMediaItem(pos, MediaItem.fromUri(url))
        exo.seekTo(pos, time)
        if (playing) exo.play()
    }
    private fun loadSeen() {
    }
    private fun saveSeen() {
    }
    private fun srcFor(v: Video): String? {
        val override = qualityOverride[v.id]
        val url = when {
            override != null -> v.renditions?.get(override) ?: dynRendition(v, override) ?: v.src
            else -> {
                val key = AutoQuality.pickKey()
                val auto = AutoQuality.urlFor(v, key)
                if (auto != null) {
                    autoKeys[v.id] = key
                    auto
                } else {
                    v.renditions?.get("720p")
                        ?: v.renditions?.get("480p")
                        ?: v.renditions?.get("360p")
                        ?: v.src
                }
            }
        }
        return fullUrl(url)
    }
    /** Dynamic rendition URL (generates on first request server-side). */
    private fun dynRendition(v: Video, res: String): String? {
        val stem = Regex("""/v/(.+)\.[a-z0-9]+$""", RegexOption.IGNORE_CASE)
            .find(v.src)?.groupValues?.get(1)
            ?.removeSuffix("-720p")?.removeSuffix("-480p")?.removeSuffix("-360p")
            ?: return null
        return "/v/$stem-$res.webm"
    }
    private fun loadMore() {
        if (loading || exhausted) return
        loading = true
        val v = view ?: return
        v.findViewById<View>(R.id.spin).visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                var added = 0
                repeat(5) {
                    if (exhausted) return@repeat
                    try {
                        val q = if (seen.isEmpty()) null else seen.takeLast(128).joinToString(",")
                        val res = ApiClient.api.wheels(q)
                        val vid = res.getAsJsonObject("video")?.let {
                            Video(
                                id = it.get("id")?.asLong ?: 0,
                                title = it.get("title")?.asString ?: "",
                                username = it.get("username")?.asString ?: "",
                                userId = it.get("user_id")?.asLong ?: 0,
                                src = it.get("src")?.asString ?: "",
                                thumbnail = it.get("thumbnail")?.asString,
                                views = it.get("views")?.asLong ?: 0,
                                likes = it.get("likes")?.asLong ?: 0,
                                liked = it.get("liked")?.asBoolean == true,
                                followers = it.get("followers")?.asLong ?: 0,
                                following = it.get("following")?.asBoolean == true,
                                created_at = it.get("created_at")?.asString ?: "",
                                avatar = it.get("avatar")?.asString,
                                kind = it.get("kind")?.asString ?: "wheel",
                            )
                        }
                        if (vid == null || vid.id == 0L || seen.contains(vid.id)) return@repeat
                        seen.add(vid.id)
                        val url = srcFor(vid) ?: return@repeat
                        videos.add(vid)
                        player?.addMediaItem(MediaItem.fromUri(url))
                        added++
                    } catch (_: Exception) {
                    }
                }
                saveSeen()
                if (!isAdded) return@launch
                if (added == 0) {
                    exhausted = true
                    if (videos.isEmpty()) {
                        v.findViewById<View>(R.id.empty).visibility = View.VISIBLE
                    } else {
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    adapter.notifyDataSetChanged()
                    if (!prepared) {
                        player?.prepare()
                        player?.seekTo(0, 0)
                        prepared = true
                    }
                    player?.playWhenReady = true
                }
            } finally {
                loading = false
                if (isAdded) v.findViewById<View>(R.id.spin).visibility = View.GONE
            }
        }
    }
    private fun watchAgain() {
        seen.clear()
        videos.clear()
        autoKeys.clear()
        selectedPos = 0
        exhausted = false
        prepared = false
        player?.stop()
        player?.clearMediaItems()
        adapter.notifyDataSetChanged()
        view?.findViewById<ViewPager2>(R.id.pager)?.setCurrentItem(0, false)
        loadMore()
    }
    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }
    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }
    override fun onDestroyView() {
        player?.release()
        player = null
        super.onDestroyView()
    }
    inner class ReelAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val playerView: PlayerView = v.findViewById(R.id.reel_player)
            val thumb: ImageView = v.findViewById(R.id.reel_thumb)
            val title: TextView = v.findViewById(R.id.reel_title)
            val meta: TextView = v.findViewById(R.id.reel_meta)
            val like: MaterialButton = v.findViewById(R.id.reel_like)
            val mute: MaterialButton = v.findViewById(R.id.reel_mute)
            val comments: MaterialButton = v.findViewById(R.id.reel_comments)
            val quality: MaterialButton = v.findViewById(R.id.reel_quality)
        }
        inner class EndHolder(v: View) : RecyclerView.ViewHolder(v) {
            val again: View = v.findViewById(R.id.end_again)
        }
        override fun getItemViewType(position: Int): Int =
            if (position < videos.size) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            if (viewType == 1) {
                return EndHolder(inflater.inflate(R.layout.item_reel_end, parent, false))
            }
            return Holder(inflater.inflate(R.layout.item_reel, parent, false))
        }
        override fun getItemCount() =
            videos.size + if (exhausted && videos.isNotEmpty()) 1 else 0
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            if (h is EndHolder) {
                h.again.setOnClickListener { watchAgain() }
                return
            }
            h as Holder
            val vid = videos[position]
            h.playerView.player = if (position == selectedPos) player else null
            h.thumb.loadMedia(vid.thumbnail)
            h.thumb.visibility =
                if (position == selectedPos) View.GONE else View.VISIBLE
            h.title.text = vid.title
            h.meta.text = "@${vid.username} • ${fmtNum(vid.views)} views"
            h.like.setIconResource(if (vid.liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite_outline)
            h.like.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            h.like.alpha = if (vid.liked) 1.0f else 0.6f
            h.mute.setIconResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
            h.playerView.setOnClickListener { togglePlayPause() }
            h.like.setOnClickListener { toggleLike(vid, h) }
            h.mute.setOnClickListener {
                muted = !muted
                player?.volume = if (muted) 0f else 1f
                notifyDataSetChanged()
            }
            h.comments.setOnClickListener {
                (activity as? MainActivity)?.openDetail(WatchFragment.newInstance(vid.id))
            }
            h.quality.setOnClickListener { showQualityMenu(h.quality, vid) }
        }
        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            super.onViewAttachedToWindow(holder)
            if (holder is Holder) {
                holder.playerView.player =
                    if (holder.bindingAdapterPosition == selectedPos) player else null
            }
        }
        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            if (holder is Holder && holder.playerView.player != null) {
                holder.playerView.player = null
            }
            super.onViewDetachedFromWindow(holder)
        }
        private fun togglePlayPause() {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        private fun toggleLike(vid: Video, h: Holder) {
            lifecycleScope.launch {
                try {
                    val res = ApiClient.api.like(vid.id)
                    vid.liked = res.get("liked")?.asBoolean == true
                    vid.likes = res.get("likes")?.asLong ?: vid.likes
                    if (isAdded) {
                        h.like.setIconResource(if (vid.liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite_outline)
                        h.like.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                        h.like.alpha = if (vid.liked) 1.0f else 0.6f
                    }
                } catch (e: Exception) {
                    if (isAdded) view?.snack(httpErrorMessage(e))
                }
            }
        }
        private fun showQualityMenu(anchor: View, vid: Video) {
            val popup = PopupMenu(requireContext(), anchor)
            val options = mutableListOf("Auto" to null as String?)
            fun has(label: String) = options.any { it.first == label }
            vid.renditions?.get("720p")?.let { options.add("720p HD" to it) }
            vid.renditions?.get("480p")?.let { options.add("480p" to it) }
            vid.renditions?.get("360p")?.let { options.add("360p" to it) }
            if (!has("720p HD") && !has("720p")) dynRendition(vid, "720p")?.let { options.add("720p HD" to it) }
            if (!has("480p")) dynRendition(vid, "480p")?.let { options.add("480p" to it) }
            if (!has("360p")) dynRendition(vid, "360p")?.let { options.add("360p" to it) }
            options.add("Source" to vid.src)
            options.forEachIndexed { i, (label, _) -> popup.menu.add(0, i, i, label) }
            popup.setOnMenuItemClickListener { item ->
                val (label, _) = options[item.itemId]
                val key = when {
                    label.startsWith("720p") -> "720p"
                    label.startsWith("480p") -> "480p"
                    label.startsWith("360p") -> "360p"
                    else -> null
                }
                if (key == null) qualityOverride.remove(vid.id) else qualityOverride[vid.id] = key
                val exo = player ?: return@setOnMenuItemClickListener true
                val pos = videos.indexOf(vid)
                if (pos < 0) return@setOnMenuItemClickListener true
                val url = fullUrl(srcFor(vid) ?: vid.src) ?: return@setOnMenuItemClickListener true
                val wasIndex = exo.currentMediaItemIndex
                val time = exo.currentPosition
                val playing = exo.isPlaying
                exo.removeMediaItem(pos)
                exo.addMediaItem(pos, MediaItem.fromUri(url))
                if (wasIndex == pos) {
                    exo.seekTo(pos, time)
                    if (playing) exo.play()
                }
                true
            }
            popup.show()
        }
    }
}