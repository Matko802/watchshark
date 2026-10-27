@file:OptIn(ExperimentalMaterial3Api::class)

package watchshark.duckdns.org.ui.compose.player

import androidx.compose.animation.AnimatedVisibility
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
import watchshark.duckdns.org.ui.PlatformLocal
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import watchshark.duckdns.org.*
import watchshark.duckdns.org.Res
import watchshark.duckdns.org.data.PPlayer
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.compose.theme.AppMotion


data class PlayerUiState(
    val isPlaying: Boolean = false,
    val ready: Boolean = false,
    val buffering: Boolean = false,
    val volume: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
) {
    val isBuffering: Boolean get() = buffering
    val isReady: Boolean get() = ready
    val isMuted: Boolean get() = volume == 0f
}


@Composable
fun rememberPlayerUiState(player: PPlayer?): PlayerUiState {
    var isPlaying by remember(player) { mutableStateOf(player?.playing == true) }
    var readyState by remember(player) { mutableStateOf(player?.ready == true) }
    var bufferingState by remember(player) { mutableStateOf(player?.buffering == true) }
    var volume by remember(player) { mutableStateOf(player?.volume ?: 1f) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var bufferedMs by remember(player) { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (isActive) {
            try {
                positionMs = player.positionMs.coerceAtLeast(0)
                durationMs = player.durationMs.coerceAtLeast(0)
                bufferedMs = player.bufferedMs.coerceAtLeast(0)
                volume = player.volume
                isPlaying = player.playing
                readyState = player.ready
                bufferingState = player.buffering
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    return PlayerUiState(
        isPlaying = isPlaying,
        ready = readyState,
        buffering = bufferingState,
        volume = volume,
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedMs = bufferedMs,
    )
}


@Composable
expect fun VideoSurfacePlatform(
    player: PPlayer?,
    modifier: Modifier,
)

@Composable
fun VideoSurface(
    player: PPlayer?,
    modifier: Modifier = Modifier,
) {
    VideoSurfacePlatform(player, modifier)
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
        val buzzCtx = PlatformLocal.app
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
                        if (playing) Res.drawable.ic_pause else Res.drawable.ic_play_arrow,
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
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
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

@Composable
fun ScrubBar(
    fraction: Float,
    bufferedFraction: Float,
    controlsActive: Boolean,
    enabled: Boolean,
    onScrub: () -> Unit,
    onSeek: (Float) -> Unit,
    waveOn: Boolean = false,
    modifier: Modifier = Modifier
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val shown = (if (scrubbing) scrubValue else fraction).coerceIn(0f, 1f)
    val buffered = bufferedFraction.coerceIn(0f, 1f)
    val active = scrubbing || controlsActive
    val wavy = active || waveOn
    var waveAmp by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(wavy) {
        val target = if (wavy) 1f else 0f
        val start = waveAmp
        val steps = 12
        repeat(steps) { i ->
            waveAmp = start + (target - start) * (i + 1) / steps
            delay(16)
        }
        waveAmp = target
    }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(wavy) {
        while (wavy) {
            delay(16)
            phase = (phase + 0.118f) % (2f * kotlin.math.PI.toFloat())
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
                                val y = cy + kotlin.math.sin((x / waveLenPx) * 2f * kotlin.math.PI.toFloat() + phase).toFloat() * maxAmpPx * waveAmp
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
