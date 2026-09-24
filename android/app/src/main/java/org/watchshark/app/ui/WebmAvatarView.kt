package org.watchshark.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient

/**
 * Avatar that plays animated (.webm) profile pictures on loop, like the
 * website. Static images load with Coil as usual.
 */
class WebmAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val image: ImageView
    private val playerView: PlayerView
    private var player: ExoPlayer? = null

    init {
        View.inflate(context, R.layout.view_webm_avatar, this)
        image = findViewById(R.id.ava_image)
        playerView = findViewById(R.id.ava_player)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    private val avatarListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // Only cover the placeholder once frames actually render —
            // otherwise a failed stream leaves a black square.
            playerView.visibility =
                if (playbackState == Player.STATE_READY) View.VISIBLE else View.GONE
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            playerView.visibility = View.GONE
        }
    }

    fun setAvatar(path: String?, placeholder: Int = R.drawable.ic_person) {
        if (isWebm(path)) {
            // Static first frame underneath; replaced by animation on READY.
            image.loadMedia(path, placeholder)
            image.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            val exo = player ?: ApiClient.buildPlayer(context).also { player = it }.apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(avatarListener)
            }
            val url = ApiClient.fullUrl(path) ?: return
            if (exo.currentMediaItem?.localConfiguration?.uri.toString() != url) {
                exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                exo.prepare()
            }
            exo.play()
        } else {
            releasePlayer()
            playerView.visibility = View.GONE
            image.visibility = View.VISIBLE
            image.loadMedia(path, placeholder)
        }
    }

    fun release() {
        releasePlayer()
    }

    /**
     * Online presence dot (green = active, grey = offline).
     * Hidden by default — home feed never shows it.
     */
    private var dotView: View? = null

    fun setOnline(online: Boolean) {
        var dot = dotView
        if (dot == null) {
            dot = View(context).apply {
                val s = (11 * resources.displayMetrics.density).toInt()
                val m = (1 * resources.displayMetrics.density).toInt()
                layoutParams = LayoutParams(s, s).apply {
                    gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
                    marginEnd = m
                    bottomMargin = m
                }
                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.dot_bg)
            }
            addView(dot)
            dotView = dot
        }
        dot.visibility = View.VISIBLE
        val color = if (online) android.graphics.Color.parseColor("#35D05A")
        else android.graphics.Color.parseColor("#6E6E6E")
        dot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
        playerView.visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        player?.play()
    }

    override fun onDetachedFromWindow() {
        player?.pause()
        super.onDetachedFromWindow()
    }

    companion object {
        fun isWebm(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            return path.substringBefore('?').lowercase().endsWith(".webm")
        }
    }
}
