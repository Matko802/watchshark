package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.Updater

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
            "Update available${update?.let { " (${it.version})" } ?: ""}",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (error != null) {
            Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            )
            Text(
                status,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
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
