package watchshark.duckdns.org.ui.compose

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.ui.httpErrorMessage

@Composable
fun UploadScreen(
    kind0: String = "video",
    onDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember(kind0) { mutableStateOf(kind0) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun displayName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else uri.lastPathSegment ?: "file"
            } ?: (uri.lastPathSegment ?: "file")
        } catch (_: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) fileUri = uri
    }
    val pickThumb = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) thumbUri = uri
    }

    fun upload() {
        if (busy) return
        val uri = fileUri
        if (uri == null) {
            msg = "Choose a file first"
            return
        }
        if (title.trim().isEmpty()) {
            msg = "Title is required"
            return
        }
        busy = true
        msg = "Uploading..."
        scope.launch {
            try {
                val cr = context.contentResolver
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val bytes = withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Cannot read file")
                }
                val filePart = MultipartBody.Part.createFormData(
                    "file", displayName(uri),
                    bytes.toRequestBody(mime.toMediaType()),
                )
                var thumbPart: MultipartBody.Part? = null
                thumbUri?.let { tu ->
                    val tm = cr.getType(tu) ?: "image/jpeg"
                    val tb = withContext(Dispatchers.IO) {
                        cr.openInputStream(tu)?.use { it.readBytes() }
                    }
                    if (tb != null) {
                        thumbPart = MultipartBody.Part.createFormData(
                            "thumb", displayName(tu), tb.toRequestBody(tm.toMediaType()),
                        )
                    }
                }
                val res = ApiClient.api.upload(
                    title.trim().toRequestBody("text/plain".toMediaType()),
                    desc.toRequestBody("text/plain".toMediaType()),
                    kind.toRequestBody("text/plain".toMediaType()),
                    filePart, thumbPart,
                )
                if (res.has("error") || !res.has("id")) {
                    msg = res.get("error")?.asString ?: "Upload failed"
                    return@launch
                }
                msg = "Uploaded!"
                onDone(res.get("id").asLong)
            } catch (e: Exception) {
                msg = httpErrorMessage(e)
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
            onClick = { pickFile.launch("video/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(fileUri?.let { displayName(it) } ?: "Choose video file")
        }
        OutlinedButton(
            onClick = { pickThumb.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(thumbUri?.let { "Thumbnail: " + displayName(it) } ?: "Choose thumbnail (optional)")
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Button(onClick = { upload() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Upload")
        }
        AnimatedVisibility(
            visible = msg.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            label = "uploadMsg",
        ) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
