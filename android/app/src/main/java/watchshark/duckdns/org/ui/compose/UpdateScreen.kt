package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.Updater
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fullscreen update page (follows the system theme like everything
 * else): expressive flower wheel, percent, size readout, cancel.
 */
@Composable
fun UpdateScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val update = remember { Updater.pending }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Starting…") }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(update) {
        if (update == null) {
            error = "No update selected."
            return@LaunchedEffect
        }
        job = scope.launch {
            try {
                val file = Updater.downloadApk(appCtx, update) { received, total ->
                    if (total > 0) {
                        progress = (received.toFloat() / total).coerceIn(0f, 1f)
                        status = "${(progress * 100).toInt()}%"
                    } else {
                        status = "Downloading…"
                    }
                }
                Updater.installDownloaded(appCtx, file, update)?.let { err ->
                    error = err
                    return@launch
                }
                Updater.pending = null
                onDone()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: "Download failed"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Updating…${update?.let { " (${it.version})" } ?: ""}",
            style = MaterialTheme.typography.headlineSmall,
        )
        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "updateError",
        ) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AnimatedVisibility(
            visible = error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            label = "updateProgress",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Expressive flower wheel (M3 wavy style): a rippling ring
            // whose lit arc follows download progress.
            FlowerProgressWheel(
                progress = progress,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                status,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            }
        }
        OutlinedButton(
            onClick = {
                job?.cancel()
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) { Text(if (error != null) "Back" else "Cancel") }
    }
}

/**
 * Expressive flower wheel in the M3 wavy style: a sine-rippled ring
 * with a travelling wave phase; the lit arc tracks [progress].
 */
@Composable
private fun FlowerProgressWheel(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val wave = rememberInfiniteTransition(label = "flowerWave")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2200)),
        label = "flowerPhase",
    )
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Canvas(modifier = modifier.size(76.dp)) {
        val stroke = size.minDimension / 13f
        val baseR = size.minDimension / 2f - stroke
        val amp = size.minDimension * 0.035f
        val ripples = 10
        val steps = 140
        fun ringPath(fraction: Float): Path {
            val path = Path()
            val sweep = (2 * PI * fraction.coerceIn(0.004f, 1f)).toFloat()
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val a = -PI.toFloat() / 2f + t * sweep
                val r = baseR + amp * sin(ripples * a + phase)
                val x = size.width / 2f + r * cos(a)
                val y = size.height / 2f + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }
        drawPath(
            path = ringPath(1f),
            color = trackColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawPath(
            path = ringPath(progress),
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
