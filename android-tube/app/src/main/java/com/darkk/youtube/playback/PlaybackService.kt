package com.darkk.youtube.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.darkk.youtube.MainActivity

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    companion object {
        var exoPlayer: ExoPlayer? = null
        var mediaSession: MediaSession? = null
        
        fun getSharedPlayer(context: android.content.Context): ExoPlayer {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context.applicationContext)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .setUsage(C.USAGE_MEDIA)
                            .build(),
                        true
                    )
                    .setHandleAudioBecomingNoisy(true)
                    .build()
            }
            return exoPlayer!!
        }
        
        fun swapPlayer(newPlayer: ExoPlayer) {
            exoPlayer = newPlayer
            mediaSession?.player = newPlayer
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val playerToUse = getSharedPlayer(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        mediaSession = MediaSession.Builder(this, playerToUse)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            exoPlayer?.release()
            exoPlayer = null
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
