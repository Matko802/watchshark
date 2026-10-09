package watchshark.duckdns.org.data

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.exoplayer.ExoPlayer

object PlayerManager {
    @Volatile
    private var player: ExoPlayer? = null
    @Volatile
    private var wasPlayingBeforePause = false

    @Synchronized
    fun get(context: Context): ExoPlayer {
        val appCtx = context.applicationContext
        return player ?: ApiClient.buildPlayer(appCtx).also { player = it }
    }

    fun pauseForBackground() {
        try {
            val p = player ?: return
            wasPlayingBeforePause = p.isPlaying
            if (p.isPlaying) p.pause()
        } catch (_: Exception) {
        }
    }

    fun resumeIfNeeded() {
        try {
            val p = player ?: return
            if (wasPlayingBeforePause) {
                p.play()
            }
            wasPlayingBeforePause = false
        } catch (_: Exception) {
        }
    }

    fun stopAndClear() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            PlayerCache.release()
        } catch (_: Exception) {
        }
    }

    fun observer(): DefaultLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            pauseForBackground()
        }

        override fun onResume(owner: LifecycleOwner) {
            resumeIfNeeded()
        }

        override fun onStop(owner: LifecycleOwner) {
            pauseForBackground()
        }
    }
}
