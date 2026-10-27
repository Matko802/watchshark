package watchshark.duckdns.org.data

import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import watchshark.duckdns.org.ui.Uikit
import java.io.InputStream

actual object UploadKit {

    actual suspend fun upload(
        req: UploadRequest,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        return try {
            val thumbBytes = req.thumb?.let { MediaKit.readBytes(it, 10L * 1024 * 1024) }
            val total = longArrayOf(0)
            val knownSize = req.file.size.takeIf { it > 0 }
            val progressSize = knownSize ?: (400L * 1024 * 1024)
            val stream = contentStream(req.file) ?: return UploadResult.Failure("Cannot read file")
            val parts = formData {
                append("title", req.title)
                append("description", req.description)
                append("kind", req.kind)
                append(
                    "file",
                    InputProvider(knownSize) {
                        countingInput(stream, total, progressSize, onProgress).asInput()
                    },
                    Headers.build {
                        append(HttpHeaders.ContentType, req.file.mime)
                        append(HttpHeaders.ContentDisposition, "filename=\"${quoteMultipartFilename(req.file.name)}\"")
                    },
                )
                if (thumbBytes != null) {
                    append(
                        "thumb",
                        thumbBytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, req.thumb?.mime ?: "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"${quoteMultipartFilename(req.thumb?.name ?: "thumb.jpg")}\"")
                        },
                    )
                }
            }
            val text = ApiClient.client.post(req.url) {
                ApiClient.authCookie()?.let { header(HttpHeaders.Cookie, it) }
                setBody(MultiPartFormDataContent(parts))
            }.bodyAsText()
            uploadResultFromJson(ApiClient.json.parseToJsonElement(text).jsonObject)
        } catch (e: CancellationException) {
            UploadResult.Cancelled
        } catch (e: Exception) {
            UploadResult.Failure(safeUploadError(e))
        }
    }

    private fun quoteMultipartFilename(name: String): String {
        return name.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun countingInput(
        stream: InputStream,
        total: LongArray,
        fileSize: Long,
        onProgress: (Float) -> Unit,
    ): InputStream = CountingStream(stream, total, onProgress, fileSize)

    private fun contentStream(file: PickedFile): InputStream? {
        return try {
            Uikit.ctx.contentResolver.openInputStream((file as AndroidPickedFile).uri)
        } catch (_: Exception) {
            null
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

private class CountingStream(
    private val upstream: InputStream,
    private val total: LongArray,
    private val onProgress: (Float) -> Unit,
    private val fileSize: Long,
) : InputStream() {
    override fun read(): Int {
        val v = upstream.read()
        if (v >= 0) tick(1) else upstream.close()
        return v
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = upstream.read(b, off, len)
        if (n > 0) tick(n.toLong()) else if (n < 0) upstream.close()
        return n
    }

    private fun tick(n: Long) {
        total[0] += n
        try {
            onProgress((total[0].toDouble() / fileSize).toFloat().coerceIn(0f, 1f))
        } catch (_: Exception) {
        }
    }

    override fun close() {
        upstream.close()
    }
}
