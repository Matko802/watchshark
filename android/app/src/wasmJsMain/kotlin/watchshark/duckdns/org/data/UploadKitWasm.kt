package watchshark.duckdns.org.data

import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.events.Event
import org.w3c.xhr.FormData
import org.w3c.xhr.ProgressEvent
import org.w3c.xhr.XMLHttpRequest
import org.w3c.files.Blob

actual object UploadKit {

    actual suspend fun upload(
        req: UploadRequest,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        return try {
            val f = (req.file as? WebPickedFile)?.jsFile
                ?: return UploadResult.Failure("Cannot read file")
            val fd = FormData()
            fd.append("title", req.title)
            fd.append("description", req.description)
            fd.append("kind", req.kind)
            fd.append("file", f, req.file.name)
            val thumbFile = req.thumb?.let { (it as? WebPickedFile)?.jsFile }
            if (thumbFile != null) {
                fd.append("thumb", thumbFile, req.thumb?.name ?: "thumb.jpg")
            }
            val totalSize = req.file.size.takeIf { it > 0 } ?: (400L * 1024 * 1024)

            val text = suspendCancellableCoroutine { cont ->
                try {
                    val xhr = XMLHttpRequest()
                    cont.invokeOnCancellation { try { xhr.abort() } catch (_: Exception) {} }
                    xhr.upload.addEventListener("progress", { ev ->
                        val pe = ev as? ProgressEvent
                        val loaded = pe?.loaded?.toDouble()?.toLong() ?: 0L
                        try {
                            onProgress((loaded.toDouble() / totalSize).coerceIn(0.0, 1.0).toFloat())
                        } catch (_: Exception) {
                        }
                    })
                    xhr.addEventListener("load", { _ ->
                        cont.resumeWith(Result.success(xhr.responseText))
                    })
                    xhr.addEventListener("error", { _ ->
                        cont.resumeWith(Result.failure(Exception("Network error")))
                    })
                    xhr.addEventListener("abort", { _ ->
                        cont.resumeWith(Result.failure(CancellationException("Upload cancelled")))
                    })
                    xhr.open("POST", req.url)
                    xhr.send(fd)
                } catch (e: Exception) {
                    cont.resumeWith(Result.failure(e))
                }
            }
            uploadResultFromJson(ApiClient.json.parseToJsonElement(text).jsonObject)
        } catch (e: CancellationException) {
            UploadResult.Cancelled
        } catch (e: Exception) {
            UploadResult.Failure(safeUploadError(e))
        }
    }

    private fun safeUploadError(e: Exception): String {
        return try {
            watchshark.duckdns.org.ui.httpErrorMessage(e)
        } catch (_: Throwable) {
            e.message ?: "Upload failed"
        }
    }
}
