@file:OptIn(UnstableApi::class)

package watchshark.duckdns.org.data

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import watchshark.duckdns.org.ui.Uikit

private fun buildExo(appCtx: Context): ExoPlayer {
    val props = mutableMapOf<String, String>()
    ApiClient.authCookie()?.let { props["Cookie"] = it }
    val dataSource = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(props)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(30000)
    try {
        AutoQuality.bindDefault(appCtx)
    } catch (_: Exception) {
    }
    return ExoPlayer.Builder(appCtx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(PlayerCache.wrap(appCtx, dataSource)))
        .build()}

private fun cachedMediaSource(appCtx: Context, url: String): MediaSource {
    val props = ApiClient.authCookie()?.let { mapOf("Cookie" to it) } ?: emptyMap()
    return PlayerCache.mediaSource(appCtx, url, props)
}

@OptIn(UnstableApi::class)
class ExoSharkPlayer(
    internal val exo: ExoPlayer,
    private val appCtx: Context,
) : PPlayer {
    override val itemCount: Int get() = exo.mediaItemCount
    override val currentIndex: Int get() = exo.currentMediaItemIndex.coerceAtLeast(0)
    override val playing: Boolean get() = exo.isPlaying
    override var playWhenReady: Boolean
        get() = exo.playWhenReady
        set(value) { exo.playWhenReady = value }
    override val positionMs: Long get() = exo.currentPosition.coerceAtLeast(0)
    override val durationMs: Long
        get() {
            val d = exo.duration
            return if (d == C.TIME_UNSET || d < 0) 0L else d
        }
    override val bufferedMs: Long
        get() {
            val b = exo.bufferedPosition
            return if (b == C.TIME_UNSET || b < 0) 0L else b
        }
    override val ready: Boolean
        get() = exo.playbackState == Player.STATE_READY || exo.playbackState == Player.STATE_ENDED
    override val buffering: Boolean
        get() = exo.playbackState == Player.STATE_BUFFERING
    override var volume: Float
        get() = exo.volume
        set(value) { exo.volume = value.coerceIn(0f, 1f) }
    override var repeatOne: Boolean
        get() = exo.repeatMode == Player.REPEAT_MODE_ONE
        set(value) {
            exo.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    override var listener: PPlayerListener? = null

    private val internalListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            listener?.onTransition(exo.currentMediaItemIndex.coerceAtLeast(0))
        }

        override fun onPlaybackStateChanged(state: Int) {
            val idx = exo.currentMediaItemIndex.coerceAtLeast(0)
            val rdy = state == Player.STATE_READY || state == Player.STATE_ENDED
            val buf = state == Player.STATE_BUFFERING
            listener?.onStateChanged(idx, rdy, buf, exo.playWhenReady)
        }
    }

    init {
        exo.addListener(internalListener)
    }

    override fun play() {
        try {
            exo.play()
        } catch (_: Exception) {
        }
    }

    override fun pause() {
        try {
            exo.pause()
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        try {
            exo.stop()
        } catch (_: Exception) {
        }
    }

    override fun seekTo(ms: Long) {
        try {
            exo.seekTo(ms.coerceAtLeast(0))
        } catch (_: Exception) {
        }
    }

    override fun seekToItem(index: Int, ms: Long) {
        try {
            exo.seekTo(index, ms.coerceAtLeast(0))
        } catch (_: Exception) {
        }
    }

    override fun switchTo(url: String) {
        try {
            val keep = exo.playbackState != Player.STATE_IDLE && durationMs > 0
            val pos = if (keep) positionMs else 0L
            val resume = if (keep) exo.playWhenReady else true
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.seekTo(pos)
            exo.playWhenReady = resume
        } catch (_: Exception) {
        }
    }

    override fun append(url: String) {
        try {
            exo.addMediaSource(cachedMediaSource(appCtx, url))
            if (exo.playbackState == Player.STATE_IDLE) {
                exo.prepare()
            }
        } catch (_: Exception) {
        }
    }

    override fun replace(index: Int, url: String) {
        try {
            val count = exo.mediaItemCount
            if (index < 0 || index >= count) return
            exo.removeMediaItem(index)
            exo.addMediaSource(index, cachedMediaSource(appCtx, url))
        } catch (_: Exception) {
        }
    }

    override fun clear() {
        try {
            exo.stop()
            exo.clearMediaItems()
        } catch (_: Exception) {
        }
    }
}

actual object PlayerManager {
    @Volatile
    private var player: ExoSharkPlayer? = null

    actual fun get(ctx: Context): PPlayer {
        val appCtx = ctx.applicationContext
        return player ?: ExoSharkPlayer(buildExo(appCtx), appCtx).also { player = it }
    }

    actual fun build(): PPlayer {
        val appCtx = Uikit.ctx.applicationContext
        return ExoSharkPlayer(buildExo(appCtx), appCtx)
    }

    actual fun pauseForBackground() {
        try {
            player?.exo?.pause()
        } catch (_: Exception) {
        }
    }

    actual fun stopAndClear() {
        try {
            player?.exo?.stop()
            player?.exo?.clearMediaItems()
        } catch (_: Exception) {
        }
    }

    actual fun release() {
        try {
            player?.exo?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            PlayerCache.release()
        } catch (_: Exception) {
        }
    }
}
