package watchshark.duckdns.org.ui.compose.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.isActive
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.compose.theme.AppMotion


data class PlayerUiState(
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val volume: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
) {
    val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING
    val isReady: Boolean get() = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED
    val isMuted: Boolean get() = volume == 0f
}


@Composable
fun rememberPlayerUiState(player: Player?): PlayerUiState {
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var playbackState by remember(player) { mutableStateOf(player?.playbackState ?: Player.STATE_IDLE) }
    var volume by remember(player) { mutableStateOf(player?.volume ?: 1f) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var bufferedMs by remember(player) { mutableLongStateOf(0L) }

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
                bufferedMs = player.bufferedPosition.coerceAtLeast(0).takeIf { it < Long.MAX_VALUE } ?: 0L
                volume = player.volume
                isPlaying = player.isPlaying
                playbackState = player.playbackState
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    return PlayerUiState(isPlaying, playbackState, volume, positionMs, durationMs, bufferedMs)
}


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


fun fmtPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, s)
    else "%d:%02d".format(java.util.Locale.US, m, s)
}

fun dynRenditionUrl(src: String, res: String): String? {
    val stem = Regex("""/v/(.+)\.[a-z0-9]+$""", RegexOption.IGNORE_CASE)
        .find(src)?.groupValues?.get(1)
        ?.removeSuffix("-720p")?.removeSuffix("-480p")?.removeSuffix("-360p")
        ?: return null
    return "/v/$stem-$res.webm"
}

object VideoPlaying {
    var isPlaying = mutableStateOf(false)
        private set

    fun setPlaying(value: Boolean) {
        isPlaying.value = value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrubBar(
    fraction: Float,
    bufferedFraction: Float,
    controlsActive: Boolean,
    enabled: Boolean,
    onScrub: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val shown = (if (scrubbing) scrubValue else fraction).coerceIn(0f, 1f)
    val buffered = bufferedFraction.coerceIn(0f, 1f)
    val active = scrubbing || controlsActive
    var waveAmp by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        val target = if (active) 1f else 0f
        val start = waveAmp
        val steps = 12
        repeat(steps) { i ->
            waveAmp = start + (target - start) * (i + 1) / steps
            delay(16)
        }
        waveAmp = target
    }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        while (active) {
            delay(16)
            phase = (phase + 0.118f) % (2f * Math.PI.toFloat())
        }
    }
    Slider(
        value = shown,
        onValueChange = {
            if (!scrubbing) scrubbing = true
            scrubValue = it
            onScrub()
        },
        onValueChangeFinished = {
            onSeek(scrubValue)
            scrubbing = false
        },
        valueRange = 0f..1f,
        enabled = enabled,
        thumb = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        val s = if (active) 1f else 0f
                        scaleX = s
                        scaleY = s
                        alpha = s
                    }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        },
        track = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val w = maxWidth
                Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    val h = size.height
                    val cy = h / 2f
                    val thinPx = 2.dp.toPx()
                    val wavePx = 4.dp.toPx()
                    val maxAmpPx = 3.5.dp.toPx()
                    val waveLenPx = 40.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = androidx.compose.ui.geometry.Offset(0f, cy),
                        end = androidx.compose.ui.geometry.Offset(size.width, cy),
                        strokeWidth = thinPx,
                        cap = StrokeCap.Round
                    )
                    if (buffered > 0.005f) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = androidx.compose.ui.geometry.Offset(0f, cy),
                            end = androidx.compose.ui.geometry.Offset(size.width * buffered, cy),
                            strokeWidth = thinPx,
                            cap = StrokeCap.Round
                        )
                    }
                    val progPx = size.width * shown
                    if (progPx > 1f) {
                        if (waveAmp > 0.02f) {
                            val path = Path()
                            val step = 2.dp.toPx().coerceAtLeast(1f)
                            var x = 0f
                            var first = true
                            while (x <= progPx) {
                                val y = cy + kotlin.math.sin((x / waveLenPx) * 2f * Math.PI.toFloat() + phase).toFloat() * maxAmpPx * waveAmp
                                if (first) {
                                    path.moveTo(x, y)
                                    first = false
                                } else {
                                    path.lineTo(x, y)
                                }
                                x += step
                            }
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(width = wavePx, cap = StrokeCap.Round)
                            )
                        } else {
                            drawLine(
                                color = Color.White,
                                start = androidx.compose.ui.geometry.Offset(0f, cy),
                                end = androidx.compose.ui.geometry.Offset(progPx, cy),
                                strokeWidth = thinPx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
