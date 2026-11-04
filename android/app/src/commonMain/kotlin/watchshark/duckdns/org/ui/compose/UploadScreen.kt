package watchshark.duckdns.org.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import watchshark.duckdns.org.ui.PlatformLocal
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import watchshark.duckdns.org.*
import watchshark.duckdns.org.Res
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AppErrors
import watchshark.duckdns.org.data.MediaKit
import watchshark.duckdns.org.data.PickedFile
import watchshark.duckdns.org.data.UploadKit
import watchshark.duckdns.org.data.UploadRequest
import watchshark.duckdns.org.data.UploadResult
import watchshark.duckdns.org.ui.apiErrorMessage
import watchshark.duckdns.org.ui.compose.theme.AppMotion

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
        if (f.size > 2048L * 1024 * 1024) {
            msg = "File too big (max 2GB)"
            return
        }
        if (!f.mime.startsWith("video/")) {
            msg = "Pick a video file for video/wheel"
            return
        }
        busy = true
        progress = 0f
        msg = ""
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

    val willCompress = file != null && file.size > 80L * 1024 * 1024
    val animProgress by animateFloatAsState(
        targetValue = if (progress < 0f) 0f else progress,
        animationSpec = tween(250),
        label = "uploadProgress",
    )
    val uploadScale by animateFloatAsState(
        targetValue = if (busy) 0.98f else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "uploadBusy",
    )

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
                onClick = { if (!busy) kind = "video" },
                label = { Text("Video") },
            )
            FilterChip(
                selected = kind == "wheel",
                onClick = { if (!busy) kind = "wheel" },
                label = { Text("Wheel") },
            )
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = desc,
            onValueChange = { desc = it },
            label = { Text("Description") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { pickVideo() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(file?.name ?: "Choose video file")
        }
        if (file != null) {
            val mb = file.size / (1024.0 * 1024.0)
            val sizeText = if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (willCompress) Color(0xFFFFB400).copy(alpha = 0.16f)
                else Color(0xFF35D05A).copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painterResource(if (willCompress) Res.drawable.ic_compress else Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = if (willCompress) Color(0xFFFFCA7A) else Color(0xFF58D97A),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (willCompress) {
                            "$sizeText — over 80 MB, will be compressed after upload"
                        } else {
                            "$sizeText — under 80 MB, publishes instantly"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (willCompress) Color(0xFFFFCA7A) else Color(0xFF58D97A),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { pickThumb() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(thumb?.let { "Thumbnail: " + it.name } ?: "Choose thumbnail (optional)")
        }
        if (busy && progress >= 0f) {
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Uploading… ${(animProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { upload() },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = uploadScale
                    scaleY = uploadScale
                },
        ) {
            Text(if (busy) "Uploading…" else "Upload")
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
