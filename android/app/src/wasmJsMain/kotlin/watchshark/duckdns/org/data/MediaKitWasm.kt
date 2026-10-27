package watchshark.duckdns.org.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import watchshark.duckdns.org.ui.Context

actual open class PickedFile actual constructor(
    actual val name: String,
    actual val mime: String,
    actual val size: Long,
)

class WebPickedFile(val jsFile: File) : PickedFile(
    jsFile.name,
    jsFile.type.ifEmpty { "application/octet-stream" },
    jsFile.size.toDouble().toLong(),
)

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

    internal fun deliver(file: WebPickedFile?) {
        val cb = pendingCallback
        pendingCallback = null
        cb?.invoke(file)
    }

    actual fun readBytes(file: PickedFile, maxBytes: Long): ByteArray? = null
}

@Composable
actual fun MediaKitHost() {
    val input = remember {
        (document.createElement("input") as HTMLInputElement).apply {
            type = "file"
            style.display = "none"
        }
    }
    LaunchedEffect(input) {
        input.addEventListener("change", { _ ->
            val f = input.files?.item(0)
            MediaKit.deliver(f?.let { WebPickedFile(it) })
            input.value = ""
        })
    }
    LaunchedEffect(Unit) {
        MediaKit.consumer = { mime ->
            try {
                input.accept = mime
                input.click()
            } catch (_: Exception) {
                MediaKit.deliver(null)
            }
        }
    }
}
