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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.Updater
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fullscreen update page (follows the system theme like everything
 * else): big progress bar, percent, size readout, cancel.
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
 * Flower progress wheel: 12 rounded petals spinning like a flower.
 * Lit petals follow download progress, trailing petals fade out.
 */
@Composable
private fun FlowerProgressWheel(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val spin = rememberInfiniteTransition(label = "flower")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1600)),
        label = "flowerRot",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(72.dp)) {
        val petals = 12
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.minDimension / 2f
        val inner = outer * 0.52f
        rotate(rotation, Offset(cx, cy)) {
            for (i in 0 until petals) {
                val angle = Math.toRadians((360f * i / petals).toDouble())
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                // Lit once download passes this petal; finished petals glow.
                val lit = progress >= (i + 1f) / petals
                drawLine(
                    color = color.copy(alpha = if (lit) 1f else 0.22f),
                    start = Offset(cx + inner * dx, cy + inner * dy),
                    end = Offset(cx + outer * dx, cy + outer * dy),
                    strokeWidth = size.minDimension / 11f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
