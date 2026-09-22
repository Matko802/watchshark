package org.watchshark.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.watchshark.app.MainActivity
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient
import org.watchshark.app.data.Comment
import org.watchshark.app.data.Video

class WatchFragment : Fragment() {
    private var videoId: Long = 0
    private var player: ExoPlayer? = null
    private var video: Video? = null
    private lateinit var commentsAdapter: CommentsAdapter

    companion object {
        fun newInstance(id: Long) = WatchFragment().apply {
            arguments = Bundle().apply { putLong("id", id) }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        videoId = arguments?.getLong("id", 0) ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        return inflater.inflate(R.layout.fragment_watch, container, false)
    }

    override fun onViewCreated(view: View, saved: Bundle?) {
        commentsAdapter = CommentsAdapter(mutableListOf())
        view.findViewById<RecyclerView>(R.id.comments).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
        }
        view.findViewById<Button>(R.id.comment_send).setOnClickListener { sendComment() }
        view.findViewById<Button>(R.id.like_btn).setOnClickListener { toggleLike() }
        view.findViewById<Button>(R.id.follow_btn).setOnClickListener { toggleFollow() }
        view.findViewById<Button>(R.id.del_btn).setOnClickListener { askDelete() }
        view.findViewById<Button>(R.id.edit_btn).setOnClickListener { askEdit() }
        view.findViewById<View>(R.id.username).setOnClickListener {
            video?.let { (activity as? MainActivity)?.openDetail(ChannelFragment.newInstance(it.username)) }
        }
        load()
    }

    private fun load() {
        val v = view ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.videoDetail(videoId)
                if (!isAdded) return@launch
                video = res.video
                render()
                commentsAdapter.setItems(res.comments)
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    private fun render() {
        val v = view ?: return
        val vid = video ?: return
        v.findViewById<TextView>(R.id.title).text = vid.title
        v.findViewById<TextView>(R.id.username).text = "@${vid.username}"
        v.findViewById<TextView>(R.id.stats).text =
            "${fmtNum(vid.views)} views • ${fmtAge(vid.created_at)} • ${fmtNum(vid.likes)} likes"
        v.findViewById<TextView>(R.id.desc).text = vid.description ?: ""
        v.findViewById<ImageView>(R.id.avatar).loadMedia(vid.avatar, R.drawable.ic_person)
        syncLike()
        lifecycleScope.launch {
            try {
                val me = ApiClient.api.me().user
                if (!isAdded) return@launch
                val follow: Button = v.findViewById(R.id.follow_btn)
                if (me != null && me.id != vid.userId) {
                    follow.visibility = View.VISIBLE
                    follow.text = if (vid.following) "Following" else "Follow"
                }
                val mine = me != null && (me.id == vid.userId || me.admin)
                v.findViewById<View>(R.id.mod_row).visibility = if (mine) View.VISIBLE else View.GONE
            } catch (_: Exception) {
            }
        }
        startPlayer(fullUrl(vid.src) ?: return)
    }

    private fun startPlayer(url: String) {
        val v = view ?: return
        releasePlayer()
        val pv: androidx.media3.ui.PlayerView = v.findViewById(R.id.player)
        player = ExoPlayer.Builder(requireContext()).build().also { exo ->
            pv.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
        }
        pv.setFullscreenButtonClickListener { toggleFullscreen() }
        pv.findViewById<android.widget.Button>(R.id.exo_qual)?.setOnClickListener { anchor ->
            showQualityMenu(anchor)
        }
        pv.findViewById<android.widget.Button>(R.id.exo_speed)?.setOnClickListener { anchor ->
            cycleSpeed(anchor as android.widget.Button)
        }
    }

    private val SPEEDS = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.5f)
    private var speedIdx = 0

    private fun cycleSpeed(btn: android.widget.Button) {
        speedIdx = (speedIdx + 1) % SPEEDS.size
        player?.setPlaybackSpeed(SPEEDS[speedIdx])
        btn.text = (if (SPEEDS[speedIdx] % 1f == 0f) SPEEDS[speedIdx].toInt().toString() else SPEEDS[speedIdx].toString()) + "x"
    }

    private fun qualityOptions(): List<Pair<String, String?>> {
        val vid = video ?: return listOf("Auto" to null)
        val o = mutableListOf("Auto" to null as String?)
        vid.renditions?.get("720p")?.let { o.add("720p HD" to it) }
        vid.renditions?.get("480p")?.let { o.add("480p" to it) }
        vid.renditions?.get("360p")?.let { o.add("360p" to it) }
        o.add("Source" to vid.src)
        return o
    }

    private fun showQualityMenu(anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        val options = qualityOptions()
        val cur = (view?.findViewById<android.widget.Button>(R.id.exo_qual)?.text ?: "Auto").toString()
        options.forEachIndexed { i, (label, _) -> popup.menu.add(0, i, i, label).isChecked = label == cur }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val (label, src) = options[item.itemId]
            view?.findViewById<android.widget.Button>(R.id.exo_qual)?.text = label
            val exo = player
            if (src != null && exo != null) {
                val t = exo.currentPosition
                val playing = exo.isPlaying
                exo.setMediaItem(MediaItem.fromUri(fullUrl(src) ?: src))
                exo.prepare()
                exo.seekTo(t)
                if (playing) exo.play()
            }
            true
        }
        popup.show()
    }

    private fun toggleFullscreen() {
        val act = activity ?: return
        val decor = act.window.decorView
        val controller = androidx.core.view.WindowInsetsControllerCompat(act.window, decor)
        if (act.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroyView() {
        releasePlayer()
        super.onDestroyView()
    }

    private fun syncLike() {
        val vid = video ?: return
        view?.findViewById<MaterialButton>(R.id.like_btn)?.apply {
            text = "${if (vid.liked) "♥ " else ""}${fmtNum(vid.likes)}"
        }
    }

    private fun toggleLike() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.like(videoId)
                if (!isAdded) return@launch
                video?.liked = res.get("liked")?.asBoolean == true
                video?.likes = res.get("likes")?.asLong ?: 0
                syncLike()
                load()
            } catch (e: Exception) {
                if (isAdded) view?.snack(httpErrorMessage(e))
            }
        }
    }

    private fun toggleFollow() {
        val vid = video ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.api.follow(vid.userId)
                if (!isAdded) return@launch
                vid.following = res.get("following")?.asBoolean == true
                vid.followers = res.get("followers")?.asLong ?: 0
                render()
            } catch (e: Exception) {
                if (isAdded) view?.snack(httpErrorMessage(e))
            }
        }
    }

    private fun sendComment() {
        val v = view ?: return
        val box = v.findViewById<TextInputEditText>(R.id.comment_box)
        val body = box.text.toString().trim()
        if (body.isEmpty()) return
        lifecycleScope.launch {
            try {
                ApiClient.api.comment(videoId, mapOf("body" to body))
                if (!isAdded) return@launch
                box.text?.clear()
                load()
            } catch (e: Exception) {
                if (isAdded) v.snack(httpErrorMessage(e))
            }
        }
    }

    private fun askDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete this video?")
            .setMessage("It will be removed permanently.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        ApiClient.api.deleteVideo(videoId, mapOf("reason" to ""))
                        if (!isAdded) return@launch
                        parentFragmentManager.popBackStack()
                    } catch (e: Exception) {
                        if (isAdded) view?.snack(httpErrorMessage(e))
                    }
                }
            }
            .show()
    }

    private fun askEdit() {
        val vid = video ?: return
        val ctx = requireContext()
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val titleIn = TextInputEditText(ctx).apply { setText(vid.title); hint = "Title" }
        val descIn = TextInputEditText(ctx).apply { setText(vid.description ?: ""); hint = "Description" }
        layout.addView(titleIn)
        layout.addView(descIn)
        AlertDialog.Builder(ctx)
            .setTitle("Edit video")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val title = titleIn.text.toString().trim()
                if (title.isEmpty()) {
                    view?.snack("Title required")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        val tb = title.toRequestBody("text/plain".toMediaType())
                        val db = descIn.text.toString().toRequestBody("text/plain".toMediaType())
                        ApiClient.api.editVideo(videoId, tb, db)
                        if (!isAdded) return@launch
                        load()
                    } catch (e: Exception) {
                        if (isAdded) view?.snack(httpErrorMessage(e))
                    }
                }
            }
            .show()
    }

    class CommentsAdapter(private val items: MutableList<Comment>) :
        RecyclerView.Adapter<CommentsAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: ImageView = v.findViewById(R.id.c_avatar)
            val user: TextView = v.findViewById(R.id.c_user)
            val body: TextView = v.findViewById(R.id.c_body)
            val time: TextView = v.findViewById(R.id.c_time)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val c = items[position]
            h.avatar.loadMedia(c.avatar, R.drawable.ic_person)
            h.user.text = "@${c.username}"
            h.body.text = c.body
            h.time.text = fmtAge(c.created_at)
        }

        fun setItems(list: List<Comment>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }
}
