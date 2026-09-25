package watchshark.duckdns.org.ui
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.data.Video
class WatchFragment : Fragment() {
    private var videoId: Long = 0
    private var player: ExoPlayer? = null
    private var video: Video? = null
    private lateinit var commentsAdapter: CommentsAdapter
    /** Adaptive quality state: manual picks disable auto for this video. */
    private var autoMode = true
    private var autoKey: String? = null
    private var everReady = false
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
        view.clearBottomBar()
        commentsAdapter = CommentsAdapter { c -> askReply(c) }
        view.findViewById<RecyclerView>(R.id.comments).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
        }
        view.findViewById<Button>(R.id.comment_send).setOnClickListener { sendComment() }
        view.findViewById<Button>(R.id.like_btn).setOnClickListener { toggleLike() }
        view.findViewById<Button>(R.id.follow_btn).setOnClickListener { toggleFollow() }
        view.findViewById<Button>(R.id.manage_btn).setOnClickListener { anchor -> showManageMenu(anchor) }
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
                commentsAdapter.setItems(res.comments.orEmpty())
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
        v.findViewById<WebmAvatarView>(R.id.avatar).setAvatar(vid.avatar, R.drawable.ic_person)
        v.findViewById<ImageView>(R.id.web_poster)?.let { poster ->
            poster.loadMedia(vid.thumbnail)
            poster.visibility = View.VISIBLE
        }
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
        startPlayer(fullUrl(autoSrc(vid)) ?: return)
    }
    /** Auto quality: rung picked from live connection speed (see AutoQuality). */
    private fun autoSrc(vid: Video): String {
        val key = watchshark.duckdns.org.data.AutoQuality.pickReadyKey(vid)
        autoKey = key
        return watchshark.duckdns.org.data.AutoQuality.readyUrl(vid, key)
            ?: vid.renditions?.get("720p")
            ?: vid.renditions?.get("480p")
            ?: vid.renditions?.get("360p")
            ?: vid.src
    }
    private fun startPlayer(url: String) {
        val v = view ?: return
        releasePlayer()
        autoMode = true
        everReady = false
        val pv: PlayerView = v.findViewById(R.id.player)
        pv.useController = true
        pv.controllerShowTimeoutMs = 3000
        pv.controllerHideOnTouch = true
        val big: MaterialButton = v.findViewById(R.id.web_bigplay)
        big.setOnClickListener { player?.play() }
        player = ApiClient.buildPlayer(requireContext()).also { exo ->
            pv.player = exo
            exo.addListener(ctrlListener)
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
            syncCtrlButtons()
        }
        wirePlayerControls(pv)
        ctrlHandler.post(ctrlTick)
    }
    /** Wire the web-like controller buttons of a PlayerView (inline or fullscreen). */
    private fun wirePlayerControls(pv: PlayerView) {
        pv.findViewById<ImageButton>(R.id.web_play)?.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        pv.findViewById<android.widget.Button>(R.id.exo_qual)?.setOnClickListener { anchor ->
            showQualityMenu(anchor)
        }
        pv.findViewById<android.widget.Button>(R.id.exo_speed)?.setOnClickListener { anchor ->
            cycleSpeed(anchor as android.widget.Button)
        }
        pv.setFullscreenButtonClickListener { toggleFullscreen() }
    }
    private val ctrlHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ctrlTick = object : Runnable {
        override fun run() {
            updateCtrlTime()
            autoTick()
            ctrlHandler.postDelayed(this, 500)
        }
    }
    /** Periodic upgrade: faster internet mid-video steps quality up. */
    private fun autoTick() {
        val exo = player ?: return
        val vid = video ?: return
        if (!autoMode || !exo.playWhenReady || exo.playbackState != Player.STATE_READY) return
        watchshark.duckdns.org.data.AutoQuality.maybeUpgradeSingle(exo, vid, autoKey)?.let {
            autoKey = it
        }
    }
    private val ctrlListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncCtrlButtons()
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                everReady = true
                view?.findViewById<View>(R.id.web_poster)?.visibility = View.GONE
            } else if (state == Player.STATE_BUFFERING) {
                val exo = player
                val vid = video
                if (exo != null && vid != null && autoMode && everReady && exo.playWhenReady) {
                    watchshark.duckdns.org.data.AutoQuality.stepDownSingle(exo, vid, autoKey)?.let {
                        autoKey = it
                    }
                }
            }
            syncCtrlButtons()
        }
    }
    private fun syncCtrlButtons() {
        val exo = player
        val playing = exo?.isPlaying == true
        playerViews().forEach { pv ->
            pv.findViewById<ImageButton>(R.id.web_play)?.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )
        }
        view?.findViewById<View>(R.id.web_bigplay)?.visibility =
            if (playing) View.GONE else View.VISIBLE
        updateCtrlTime()
    }
    private fun updateCtrlTime() {
        val exo = player ?: return
        val d = exo.duration
        if (d <= 0 || d == C.TIME_UNSET) return
        val c = exo.currentPosition.coerceAtLeast(0)
        playerViews().forEach { pv ->
            pv.findViewById<TextView>(R.id.web_time)?.text =
                "${fmtDur(c / 1000)} / ${fmtDur(d / 1000)}"
        }
    }
    /** Inline player view plus the fullscreen one when open. */
    private fun playerViews(): List<PlayerView> =
        listOfNotNull(view?.findViewById(R.id.player), fsPlayerView)
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
        fun has(label: String) = o.any { it.first == label }
        vid.renditions?.get("720p")?.let { o.add("720p HD" to it) }
        vid.renditions?.get("480p")?.let { o.add("480p" to it) }
        vid.renditions?.get("360p")?.let { o.add("360p" to it) }
        dynStem(vid.src)?.let { stem ->
            if (!has("720p HD") && !has("720p")) o.add("720p HD" to "/v/$stem-720p.webm")
            if (!has("480p")) o.add("480p" to "/v/$stem-480p.webm")
            if (!has("360p")) o.add("360p" to "/v/$stem-360p.webm")
        }
        o.add("Source" to vid.src)
        return o
    }
    private fun dynStem(src: String): String? {
        val m = Regex("""/v/(.+)\.[a-z0-9]+$""", RegexOption.IGNORE_CASE).find(src) ?: return null
        return m.groupValues[1].removeSuffix("-720p").removeSuffix("-480p").removeSuffix("-360p")
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
            autoMode = (src == null)
            if (exo == null) return@setOnMenuItemClickListener true
            if (src != null) {
                val t = exo.currentPosition
                val playing = exo.isPlaying
                exo.setMediaItem(MediaItem.fromUri(fullUrl(src) ?: src))
                exo.prepare()
                exo.seekTo(t)
                if (playing) exo.play()
            } else {
                val vid = video
                if (vid != null) {
                    val want = watchshark.duckdns.org.data.AutoQuality.pickReadyKey(vid)
                    if (want != autoKey) {
                        watchshark.duckdns.org.data.AutoQuality.readyUrl(vid, want)?.let { url ->
                            watchshark.duckdns.org.data.AutoQuality.switchSingle(exo, url)
                            autoKey = want
                        }
                    }
                }
            }
            true
        }
        popup.show()
    }
    private fun toggleFullscreen() {
        if (fsDialog != null) {
            exitFullscreen()
            return
        }
        val act = activity ?: return
        val exo = player ?: return
        val pv: PlayerView = view?.findViewById(R.id.player) ?: return
        act.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val fsView = android.view.LayoutInflater.from(act)
            .inflate(R.layout.view_fs_player, null) as PlayerView
        val dialog = android.app.Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(fsView)
        dialog.setOnDismissListener { if (fsDialog != null) exitFullscreen() }
        dialog.setOnShowListener {
            dialog.window?.let { w ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        pv.player = null
        fsView.player = exo
        wirePlayerControls(fsView)
        fsPlayerView = fsView
        fsDialog = dialog
        syncCtrlButtons()
        dialog.show()
    }
    private fun exitFullscreen() {
        val d = fsDialog ?: return
        fsDialog = null
        fsPlayerView?.player = null
        fsPlayerView = null
        view?.findViewById<PlayerView>(R.id.player)?.player = player
        if (d.isShowing) d.dismiss()
        activity?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    private var fsDialog: android.app.Dialog? = null
    private var fsPlayerView: PlayerView? = null
    private fun releasePlayer() {
        if (fsDialog != null) exitFullscreen()
        ctrlHandler.removeCallbacks(ctrlTick)
        player?.removeListener(ctrlListener)
        player?.release()
        player = null
    }
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
    override fun onDestroyView() {
        view?.findViewById<WebmAvatarView>(R.id.avatar)?.release()
        releasePlayer()
        super.onDestroyView()
    }
    private fun syncLike() {
        val vid = video ?: return
        view?.findViewById<MaterialButton>(R.id.like_btn)?.apply {
            text = fmtNum(vid.likes)
            setIconResource(
                if (vid.liked) R.drawable.ic_favorite_fill else R.drawable.ic_favorite_outline
            )
            iconTint = android.content.res.ColorStateList.valueOf(
                context.getColor(android.R.color.white)
            )
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
                video?.let { vid ->
                    view?.findViewById<TextView>(R.id.stats)?.text =
                        "${fmtNum(vid.views)} views • ${fmtAge(vid.created_at)} • ${fmtNum(vid.likes)} likes"
                }
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
    private fun askReply(to: Comment) {
        val ctx = context ?: return
        val input = TextInputEditText(ctx).apply { hint = "Reply to @${to.username}" }
        AlertDialog.Builder(ctx)
            .setTitle("Reply to @${to.username}")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reply") { _, _ ->
                val body = input.text.toString().trim()
                if (body.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        ApiClient.api.comment(
                            videoId,
                            mapOf("body" to body, "parent_id" to to.id)
                        )
                        if (!isAdded) return@launch
                        load()
                    } catch (e: Exception) {
                        if (isAdded) view?.snack(httpErrorMessage(e))
                    }
                }
            }
            .show()
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
    private fun showManageMenu(anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, "Edit")
        popup.menu.add(0, 1, 1, "Delete")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 0) askEdit() else askDelete()
            true
        }
        popup.show()
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
        val curKind = when (vid.kind) {
            "wheel" -> "wheel"
            "music" -> "music"
            else -> "video"
        }
        val kindGroup = com.google.android.material.button.MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        for ((label, key) in listOf("Videos" to "video", "Shorts" to "wheel", "Music" to "music")) {
            val b = com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = label
                tag = key
            }
            kindGroup.addView(b)
            if (key == curKind) kindGroup.check(b.id)
        }
        layout.addView(kindGroup)
        AlertDialog.Builder(ctx)
            .setTitle("Edit video")
            .setView(layout)
            .setNeutralButton("Delete") { d, _ ->
                d.dismiss()
                askDelete()
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val title = titleIn.text.toString().trim()
                if (title.isEmpty()) {
                    view?.snack("Title required")
                    return@setPositiveButton
                }
                val kindKey = kindGroup.findViewById<com.google.android.material.button.MaterialButton>(
                    kindGroup.checkedButtonId
                )?.tag as? String ?: curKind
                lifecycleScope.launch {
                    try {
                        val tb = title.toRequestBody("text/plain".toMediaType())
                        val db = descIn.text.toString().toRequestBody("text/plain".toMediaType())
                        val kb = kindKey.toRequestBody("text/plain".toMediaType())
                        ApiClient.api.editVideo(videoId, tb, db, kb)
                        if (!isAdded) return@launch
                        load()
                    } catch (e: Exception) {
                        if (isAdded) view?.snack(httpErrorMessage(e))
                    }
                }
            }
            .show()
    }
    class CommentsAdapter(
        private var rows: List<Row> = emptyList(),
        private val onReply: (Comment) -> Unit = {},
    ) : RecyclerView.Adapter<CommentsAdapter.Holder>() {
        data class Row(val c: Comment, val depth: Int)
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: WebmAvatarView = v.findViewById(R.id.c_avatar)
            val user: TextView = v.findViewById(R.id.c_user)
            val body: TextView = v.findViewById(R.id.c_body)
            val time: TextView = v.findViewById(R.id.c_time)
            val reply: TextView = v.findViewById(R.id.c_reply)
            val row: View = v
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return Holder(v)
        }
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(h: Holder, position: Int) {
            val (c, depth) = rows[position]
            h.avatar.setAvatar(c.avatar, R.drawable.ic_person)
            h.avatar.setOnline(c.online)
            h.user.text = "@${c.username}"
            val prefix = if (c.parentUsername != null) "↳ @${c.parentUsername} " else ""
            h.body.text = prefix + c.body
            h.time.text = fmtAge(c.created_at)
            val indent = (12 + depth.coerceAtMost(3) * 28) *
                h.itemView.resources.displayMetrics.density
            h.row.setPadding(indent.toInt(), h.row.paddingTop, h.row.paddingRight, h.row.paddingBottom)
            h.reply.setOnClickListener { onReply(c) }
        }
        fun setItems(list: List<Comment>) {
            rows = thread(list)
            notifyDataSetChanged()
        }
        override fun onViewRecycled(h: Holder) {
            h.avatar.release()
            super.onViewRecycled(h)
        }
        /** Thread flat comments: top-level first, replies nested under parents. */
        private fun thread(list: List<Comment>): List<Row> {
            val byId = list.associateBy { it.id }
            val children = mutableMapOf<Long, MutableList<Comment>>()
            val top = mutableListOf<Comment>()
            list.forEach { c ->
                val p = c.parentId?.let { byId[it] }
                if (p != null) children.getOrPut(p.id) { mutableListOf() }.add(c)
                else top.add(c)
            }
            val out = mutableListOf<Row>()
            fun add(c: Comment, depth: Int) {
                out.add(Row(c, depth))
                children[c.id]?.forEach { add(it, depth + 1) }
            }
            top.forEach { add(it, 0) }
            return out
        }
    }
}