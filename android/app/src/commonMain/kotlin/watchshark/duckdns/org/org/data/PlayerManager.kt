package watchshark.duckdns.org.data

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

object PlayerManager {
    @Volatile
    private var player: ExoPlayer? = null

    @Synchronized
    fun get(context: Context): ExoPlayer {
        val appCtx = context.applicationContext
        return player ?: ApiClient.buildPlayer(appCtx).also { player = it }
    }

    fun pauseForBackground() {
        try {
            player?.pause()
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
}
