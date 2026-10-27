package watchshark.duckdns.org.data

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import watchshark.duckdns.org.ui.Context
import watchshark.duckdns.org.ui.Uikit

actual open class PickedFile actual constructor(
    actual val name: String,
    actual val mime: String,
    actual val size: Long,
)

class AndroidPickedFile(val uri: Uri) : PickedFile(
    nameFor(Uikit.ctx, uri),
    mimeFor(Uikit.ctx, uri),
    sizeFor(Uikit.ctx, uri),
)

private fun nameFor(ctx: Context, uri: Uri): String {
    return try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            else uri.lastPathSegment ?: "file"
        } ?: (uri.lastPathSegment ?: "file")
    } catch (_: Exception) {
        uri.lastPathSegment ?: "file"
    }
}

private fun sizeFor(ctx: Context, uri: Uri): Long {
    return try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0) c.getLong(idx) else -1L
            } else -1L
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }
}

private fun mimeFor(ctx: Context, uri: Uri): String {
    return try {
        ctx.contentResolver.getType(uri) ?: "application/octet-stream"
    } catch (_: Exception) {
        "application/octet-stream"
    }
}

actual object MediaKit {
    internal var consumer: ((String) -> Unit)? = null
    internal var pendingCallback: ((PickedFile?) -> Unit)? = null

    actual fun pick(ctx: Context, mime: String, onPicked: (PickedFile?) -> Unit) {
        val current = consumer
        if (current == null) {
            onPicked(null)
            return
        }
        pendingCallback = onPicked
        try {
            current(mime)
        } catch (_: Exception) {
            pendingCallback = null
            onPicked(null)
        }
    }

    internal fun deliver(uri: Uri?) {
        val cb = pendingCallback
        pendingCallback = null
        if (uri == null) {
            cb?.invoke(null)
        } else {
            cb?.invoke(AndroidPickedFile(uri))
        }
    }

    actual fun readBytes(file: PickedFile, maxBytes: Long): ByteArray? {
        val uri = (file as? AndroidPickedFile)?.uri ?: return null
        return try {
            val stream = Uikit.ctx.contentResolver.openInputStream(uri) ?: return null
            stream.use { input ->
                val buf = input.readBytes()
                if (buf.size > maxBytes) null else buf
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
actual fun MediaKitHost() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        MediaKit.deliver(uri)
    }
    LaunchedEffect(Unit) {
        MediaKit.consumer = { mime -> launcher.launch(mime) }
    }
}
