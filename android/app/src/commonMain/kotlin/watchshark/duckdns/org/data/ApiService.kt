package watchshark.duckdns.org.data

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import watchshark.duckdns.org.ui.Uikit

class ApiService(
    private val base: String = Uikit.baseUrl.trimEnd('/') + "/",
) {
    private suspend inline fun <reified T> tx(
        method: HttpMethod,
        path: String,
        body: JsonElement? = null,
        q: Map<String, String?> = emptyMap(),
    ): T {
        val text = ApiClient.client.request(base + path) {
            this.method = method
            q.forEach { (k, v) -> if (v != null) parameter(k, v) }
            ApiClient.authCookie()?.let { header(HttpHeaders.Cookie, it) }
            if (body != null) {
                setBody(body)
                contentType(ContentType.Application.Json)
            }
        }.bodyAsText()
        return ApiClient.json.decodeFromString(text)
    }

    private suspend fun jtx(
        method: HttpMethod,
        path: String,
        body: Map<String, *>? = null,
        q: Map<String, String> = emptyMap(),
    ): JsonObject {
        return if (body != null) {
            tx(method, path, bodyObj(body), q)
        } else {
            tx(method, path, null as JsonElement?, q)
        }
    }

    suspend fun me(): MeResponse = tx(HttpMethod.Get, "api/me")

    private fun captureSession(response: io.ktor.client.statement.HttpResponse) {
        try {
            val cookies = response.headers.getAll("Set-Cookie") ?: return
            for (c in cookies) {
                val m = Regex("ws_token=([^;\\s]+)").find(c) ?: continue
                val token = m.groupValues[1]
                if (token.isNotBlank()) {
                    Uikit.setSessionToken(token)
                    return
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun jsonOf(response: io.ktor.client.statement.HttpResponse): JsonObject {
        val text = response.bodyAsText()
        return ApiClient.json.decodeFromString(text)
    }

    suspend fun login(b: Map<String, String>): JsonObject {
        val resp = ApiClient.client.post(base + "api/auth/login") {
            setBody(bodyObj(b))
            contentType(ContentType.Application.Json)
        }
        captureSession(resp)
        return jsonOf(resp)
    }

    suspend fun signup(b: Map<String, String>): JsonObject {
        val resp = ApiClient.client.post(base + "api/auth/signup") {
            setBody(bodyObj(b))
            contentType(ContentType.Application.Json)
        }
        captureSession(resp)
        return jsonOf(resp)
    }

    suspend fun logout(): JsonObject {
        val out = jtx(HttpMethod.Post, "api/auth/logout")
        try {
            Uikit.setSessionToken(null)
        } catch (_: Exception) {
        }
        return out
    }

    suspend fun videos(
        q: String?,
        sort: String?,
        page: Long,
        limit: Int,
        kind: String?,
    ): VideosResponse {
        val qq = buildMap {
            put("q", q)
            put("sort", sort)
            put("page", page.toString())
            put("limit", limit.toString())
            put("kind", kind)
        }
        return tx(HttpMethod.Get, "api/videos", null, qq)
    }

    suspend fun wheels(seen: String?): JsonObject {
        val qq = if (seen != null) mapOf("seen" to seen) else emptyMap()
        return jtx(HttpMethod.Get, "api/wheels", null, qq)
    }

    suspend fun videoDetail(id: Long): VideoDetailResponse = tx(HttpMethod.Get, "api/videos/$id")
    suspend fun channel(name: String): ChannelResponse = tx(HttpMethod.Get, "api/channel/$name")

    suspend fun follow(id: Long): JsonObject = jtx(HttpMethod.Post, "api/follow/$id")
    suspend fun like(id: Long): JsonObject = jtx(HttpMethod.Post, "api/videos/$id/like")

    suspend fun comment(id: Long, b: Map<String, Any>): JsonObject =
        jtx(HttpMethod.Post, "api/videos/$id/comments", b)

    suspend fun editVideo(
        id: Long,
        title: String,
        desc: String,
        kind: String,
    ): JsonObject {
        val parts = formData {
            append("title", title)
            append("description", desc)
            append("kind", kind)
        }
        val text = ApiClient.client.post(base + "api/videos/$id/edit") {
            header(HttpHeaders.Cookie, ApiClient.authCookie() ?: "")
            setBody(MultiPartFormDataContent(parts))
        }.bodyAsText()
        return ApiClient.json.decodeFromString(text)
    }

    suspend fun deleteVideo(id: Long, b: Map<String, String>): JsonObject {
        val text = ApiClient.client.delete(base + "api/videos/$id") {
            header(HttpHeaders.Cookie, ApiClient.authCookie() ?: "")
            setBody(bodyObj(b))
            contentType(ContentType.Application.Json)
        }.bodyAsText()
        return ApiClient.json.decodeFromString(text)
    }

    suspend fun adminUsers(): AdminUsersResponse = tx(HttpMethod.Get, "api/admin/users")
    suspend fun adminBan(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/admin/ban", b)
    suspend fun adminUnban(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/admin/unban", b)
    suspend fun adminApprove(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/admin/approve", b)
    suspend fun adminRestore(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/admin/restore", b)
    suspend fun adminSoftDelete(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/admin/soft-delete", b)
    suspend fun adminDelUser(id: Long): JsonObject = jtx(HttpMethod.Delete, "api/admin/users/$id")

    suspend fun notifications(): NotificationsResponse = tx(HttpMethod.Get, "api/notifications")
    suspend fun notifRead(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/notifications/read", b)
    suspend fun notifSet(b: Map<String, Boolean>): JsonObject = jtx(HttpMethod.Post, "api/settings/notifications", b)
    suspend fun rename(b: Map<String, String>): JsonObject = jtx(HttpMethod.Post, "api/auth/username", b)
    suspend fun changePw(b: Map<String, String>): JsonObject = jtx(HttpMethod.Post, "api/auth/change", b)

    suspend fun dmConversations(): ConversationsResponse = tx(HttpMethod.Get, "api/dm/conversations")
    suspend fun dmThread(
        user: String,
        after: Long?,
        before: Long?,
        limit: Int?,
    ): DmThreadResponse {
        val qq = buildMap {
            put("user", user)
            after?.let { put("after_id", it.toString()) }
            before?.let { put("before_id", it.toString()) }
            limit?.let { put("limit", it.toString()) }
        }
        return tx(HttpMethod.Get, "api/dm/thread", null, qq)
    }

    suspend fun dmSend(b: Map<String, String>): JsonObject = jtx(HttpMethod.Post, "api/dm/send", b)
    suspend fun dmRead(b: Map<String, Any?>): JsonObject = jtx(HttpMethod.Post, "api/dm/read", b)
    suspend fun dmUnread(): DmUnreadResponse = tx(HttpMethod.Get, "api/dm/unread")
    suspend fun dmUserSearch(q: String): DmUserSearchResponse {
        val qq = mapOf("q" to q)
        return tx(HttpMethod.Get, "api/users/search", null, qq)
    }
}
