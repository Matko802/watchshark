package watchshark.duckdns.org.data

import kotlinx.serialization.json.JsonObject
import watchshark.duckdns.org.ui.optLong
import watchshark.duckdns.org.ui.optStr

sealed interface UploadResult {
    data class Success(val id: Long) : UploadResult
    data class Failure(val message: String) : UploadResult
    data object Cancelled : UploadResult
}

data class UploadRequest(
    val url: String,
    val title: String,
    val description: String,
    val kind: String,
    val file: PickedFile,
    val thumb: PickedFile?,
)

expect object UploadKit {
    suspend fun upload(req: UploadRequest, onProgress: (Float) -> Unit): UploadResult
}

internal fun uploadResultFromJson(obj: JsonObject): UploadResult {
    val err = obj.optStr("error")
    if (err != null) return UploadResult.Failure(err)
    val id = obj.optLong("id")
        ?: return UploadResult.Failure("Upload failed")
    return UploadResult.Success(id)
}
