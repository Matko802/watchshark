package watchshark.duckdns.org.ui.compose.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.compose.theme.AppMotion

/** Snapshot of player state for Compose controls. */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val volume: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING
    val isReady: Boolean get() = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED
    val isMuted: Boolean get() = volume == 0f
}

/**
 * Observes an ExoPlayer: instant updates for play/pause/buffering via
 * listener, plus a 500ms poll for position/duration/volume.
 */
@Composable
fun rememberPlayerUiState(player: Player?): PlayerUiState {
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var playbackState by remember(player) { mutableStateOf(player?.playbackState ?: Player.STATE_IDLE) }
    var volume by remember(player) { mutableStateOf(player?.volume ?: 1f) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (isActive) {
            try {
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = player.duration.coerceAtLeast(0).takeIf { it < Long.MAX_VALUE } ?: 0L
                volume = player.volume
                isPlaying = player.isPlaying
                playbackState = player.playbackState
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    return PlayerUiState(isPlaying, playbackState, volume, positionMs, durationMs)
}

/** Bare video surface (web parity: no stock controller, Compose draws UI). */
@Composable
fun VideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view -> view.player = player },
        modifier = modifier,
    )
}

/** Big center play/pause button, like web's #bigplay fab. No highlight. */
@Composable
fun CenterPlayButton(
    visible: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(AppMotion.fastSpatial, initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(targetScale = 0.6f) + fadeOut(),
        modifier = modifier,
        label = "centerPlay",
    ) {
        val buzzCtx = LocalContext.current
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        Haptics.tick(buzzCtx)
                        onToggle()
                    },
                ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/** Themed buffering spinner over video. */
@Composable
fun BufferingSpinner(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
        label = "buffering",
    ) {
        CircularProgressIndicator(color = tint, modifier = Modifier.size(40.dp))
    }
}

/** m:ss (or h:mm:ss) time readout, like web's time display. */
fun fmtPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
