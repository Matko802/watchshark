package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import watchshark.duckdns.org.ui.PlatformLocal
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AppErrors
import watchshark.duckdns.org.data.MediaKit
import watchshark.duckdns.org.data.PickedFile
import watchshark.duckdns.org.data.UploadKit
import watchshark.duckdns.org.data.UploadRequest
import watchshark.duckdns.org.data.UploadResult
import watchshark.duckdns.org.ui.apiErrorMessage

@Composable
fun UploadScreen(
    kind0: String = "video",
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = PlatformLocal.app
    val scope = rememberCoroutineScope()
    var kind by remember(kind0) { mutableStateOf(if (kind0 == "wheel") "wheel" else "video") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var thumb by remember { mutableStateOf<PickedFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun pickVideo() {
        MediaKit.pick(context, "video/*") { picked ->
            if (picked != null) file = picked
        }
    }

    fun pickThumb() {
        MediaKit.pick(context, "image/*") { picked ->
            if (picked != null) thumb = picked
        }
    }

    fun upload() {
        if (busy) return
        val f = file
        if (f == null) {
            msg = "Choose a file first"
            return
        }
        if (title.trim().isEmpty()) {
            msg = "Title is required"
            return
        }
        if (title.trim().length > 120) {
            msg = "Title must be under 120 characters"
            return
        }
        if (f.size > 500L * 1024 * 1024) {
            msg = "File too big (max 500MB)"
            return
        }
        if (!f.mime.startsWith("video/")) {
            msg = "Pick a video file for video/wheel"
            return
        }
        busy = true
        progress = 0f
        msg = "Uploading..."
        job = scope.launch {
            try {
                val result = UploadKit.upload(
                    UploadRequest(
                        url = ApiClient.BASE_URL.trimEnd('/') + "/api/videos",
                        title = title.trim(),
                        description = desc,
                        kind = kind,
                        file = f,
                        thumb = thumb,
                    ),
                ) { p ->
                    progress = p
                    if (msg != "Uploading...") msg = "Uploading..."
                }
                when (result) {
                    is UploadResult.Success -> {
                        msg = "Uploaded!"
                        progress = 1f
                        onDone(result.id)
                    }
                    is UploadResult.Failure -> msg = result.message
                    UploadResult.Cancelled -> msg = "Upload cancelled"
                }
            } catch (e: Exception) {
                AppErrors.log(e, "upload")
                msg = apiErrorMessage(e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Upload", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = kind == "video",
                onClick = { kind = "video" },
                label = { Text("Video") },
            )
            FilterChip(
                selected = kind == "wheel",
                onClick = { kind = "wheel" },
                label = { Text("Wheel") },
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { pickVideo() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(file?.name ?: "Choose video file")
        }
        OutlinedButton(
            onClick = { pickThumb() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(thumb?.let { "Thumbnail: " + it.name } ?: "Choose thumbnail (optional)")
        }
        if (busy && progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Uploading… ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { upload() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Upload")
        }
        if (busy) {
            TextButton(onClick = { job?.cancel() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
        AnimatedVisibility(
            visible = msg.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "uploadMsg",
        ) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(
            modifier = Modifier.height(88.dp),
        )
    }
}
