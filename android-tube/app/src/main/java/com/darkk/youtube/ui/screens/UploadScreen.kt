package com.darkk.youtube.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkk.youtube.innertube.WatchSharkApi
import com.darkk.youtube.innertube.WatchSharkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onBack: () -> Unit,
    onUploaded: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("video") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun nameOf(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else uri.lastPathSegment ?: "file"
            } ?: (uri.lastPathSegment ?: "file")
        } catch (e: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fileUri = uri
            fileName = nameOf(uri)
        }
    }
    val pickThumb = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        thumbUri = uri
    }

    fun cacheCopy(uri: Uri, prefix: String): File? {
        return try {
            val name = nameOf(uri)
            val out = File.createTempFile(prefix, "_$name", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { ins ->
                out.outputStream().use { ins.copyTo(it) }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Upload", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))

        val kinds = listOf("video" to "Video", "wheel" to "Wheel", "music" to "Music")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { (value, label) ->
                FilterChip(
                    selected = kind == value,
                    onClick = { kind = value },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { pickFile.launch(if (kind == "music") "audio/*" else "video/*") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF272727), contentColor = Color.White)
        ) {
            Text(if (fileName.isEmpty()) "Choose file" else fileName)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickThumb.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (thumbUri == null) "Choose thumbnail (optional)" else "Thumbnail selected")
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (required)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val uri = fileUri
                if (uri == null) {
                    message = "Choose a file first"
                    return@Button
                }
                if (title.isBlank()) {
                    message = "Title is required"
                    return@Button
                }
                busy = true
                message = "Uploading..."
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { cacheCopy(uri, "up") }
                            ?: throw WatchSharkException("Cannot read file")
                        val mime = withContext(Dispatchers.IO) {
                            context.contentResolver.getType(uri) ?: "application/octet-stream"
                        }
                        var thumbFile: File? = null
                        var thumbMime: String? = null
                        thumbUri?.let { tu ->
                            thumbFile = withContext(Dispatchers.IO) { cacheCopy(tu, "thumb") }
                            thumbMime = withContext(Dispatchers.IO) {
                                context.contentResolver.getType(tu) ?: "image/jpeg"
                            }
                        }
                        val id = withContext(Dispatchers.IO) {
                            WatchSharkApi.uploadVideo(title.trim(), description, kind, file, mime, thumbFile, thumbMime)
                        }
                        file.delete()
                        thumbFile?.delete()
                        busy = false
                        onUploaded(id)
                    } catch (e: Exception) {
                        busy = false
                        message = e.message ?: "Upload failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text("Upload")
        }
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = Color.Gray, fontSize = 13.sp)
        }
    }
}
