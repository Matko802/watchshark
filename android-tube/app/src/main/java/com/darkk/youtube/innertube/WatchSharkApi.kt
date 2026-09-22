package com.darkk.youtube.innertube

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WatchSharkException(message: String) : Exception(message)

object WatchSharkApi {

    const val APP_URL = "https://watchshark.duckdns.org"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs by lazy {
        appContext.getSharedPreferences("ws_session", Context.MODE_PRIVATE)
    }

    fun sessionToken(): String? = prefs.getString("ws_token", null)
    fun hasSession(): Boolean = !sessionToken().isNullOrEmpty()

    fun saveToken(token: String) {
        prefs.edit().putString("ws_token", token).apply()
    }

    fun clearSession() {
        prefs.edit().remove("ws_token").apply()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    fun fullUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        return APP_URL + path
    }

    private fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.numOrNull(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun parseObj(text: String): JsonObject {
        val el = json.parseToJsonElement(text)
        val obj = el as? JsonObject ?: throw WatchSharkException("Bad response")
        obj.strOrNull("error")?.let { throw WatchSharkException(it) }
        return obj
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val text = http.get(APP_URL + path) {
            params.forEach { (k, v) -> parameter(k, v) }
            sessionToken()?.let { header(HttpHeaders.Cookie, "ws_token=$it") }
        }.bodyAsText()
        return parseObj(text)
    }

    private suspend fun postJson(path: String, body: String): JsonObject {
        val resp = http.post(APP_URL + path) {
            contentType(ContentType.Application.Json)
            sessionToken()?.let { header(HttpHeaders.Cookie, "ws_token=$it") }
            setBody(body)
        }
        resp.headers.getAll(HttpHeaders.SetCookie)?.forEach { header ->
            header.split(";").firstOrNull { it.trim().startsWith("ws_token=") }?.let {
                saveToken(it.trim().removePrefix("ws_token="))
            }
        }
        return parseObj(resp.bodyAsText())
    }

    private fun strBody(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    data class WsVideo(
        val id: Long,
        val title: String,
        val description: String,
        val username: String,
        val userId: Long,
        val src: String,
        val thumbnail: String?,
        val views: Long,
        val likes: Long,
        val liked: Boolean,
        val comments: Long,
        val followers: Long,
        val following: Boolean,
        val createdAt: String,
        val avatar: String?,
        val renditions: Map<String, String>,
        val kind: String,
    )

    data class WsComment(
        val id: Long,
        val body: String,
        val createdAt: String,
        val username: String,
        val avatar: String?,
    )

    data class WsChannel(
        val id: Long,
        val username: String,
        val avatar: String?,
        val createdAt: String,
        val followers: Long,
        val videos: Long,
        val views: Long,
        val following: Boolean,
    )

    private fun JsonObject.reqLong(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.optString(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.optBool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun parseVideo(o: JsonObject): WsVideo {
        val rend = mutableMapOf<String, String>()
        (o["renditions"] as? JsonObject)?.forEach { k, v ->
            (v as? JsonPrimitive)?.contentOrNull?.let { rend[k] = it }
        }
        return WsVideo(
            id = o.reqLong("id"),
            title = o.optString("title"),
            description = o.optString("description"),
            username = o.optString("username"),
            userId = o.reqLong("user_id"),
            src = o.optString("src"),
            thumbnail = o.strOrNull("thumbnail"),
            views = o.reqLong("views"),
            likes = o.reqLong("likes"),
            liked = o.optBool("liked"),
            comments = o.reqLong("comments"),
            followers = o.reqLong("followers"),
            following = o.optBool("following"),
            createdAt = o.optString("created_at"),
            avatar = o.strOrNull("avatar"),
            renditions = rend,
            kind = o.optString("kind").ifEmpty { "video" },
        )
    }

    private fun parseComment(o: JsonObject): WsComment = WsComment(
        id = o.reqLong("id"),
        body = o.optString("body"),
        createdAt = o.optString("created_at"),
        username = o.optString("username"),
        avatar = o.strOrNull("avatar"),
    )

    suspend fun login(login: String, password: String): Pair<Long, String> {
        val obj = postJson(
            "/api/auth/login",
            """{"login":${strBody(login)},"password":${strBody(password)}}""",
        )
        if (obj.optBool("banned")) {
            val days = (obj["ban_days_left"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
            val span = if (days < 0) "permanently" else "for %.1f days".format(days)
            val reason = obj.strOrNull("ban_reason")
            throw WatchSharkException(
                "You have been banned $span." + if (!reason.isNullOrEmpty()) "\nReason: $reason" else "",
            )
        }
        if (obj.optBool("deleted")) {
            val reason = obj.strOrNull("deleted_reason")
            throw WatchSharkException(
                "Your account has been deleted." + if (!reason.isNullOrEmpty()) "\nReason: $reason" else "",
            )
        }
        val user = obj["user"] as? JsonObject ?: throw WatchSharkException("Login failed")
        return Pair(user.reqLong("id"), user.optString("username"))
    }

    suspend fun me(): JsonObject? {
        return try {
            val obj = get("/api/me")
            obj["user"] as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logout() {
        try {
            postJson("/api/auth/logout", "{}")
        } catch (_: Exception) {
        }
        clearSession()
    }

    data class VideoPage(val videos: List<WsVideo>, val page: Long, val pages: Long)

    suspend fun listVideos(q: String?, sort: String?, page: Long, kind: String?): VideoPage {
        val params = mutableMapOf("page" to page.toString(), "limit" to "12")
        if (!q.isNullOrBlank()) params["q"] = q
        if (!sort.isNullOrBlank()) params["sort"] = sort
        if (!kind.isNullOrBlank()) params["kind"] = kind
        val obj = get("/api/videos", params)
        val videos = (obj["videos"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseVideo(o) } } ?: emptyList()
        return VideoPage(videos, obj.reqLong("page"), obj.reqLong("pages"))
    }

    data class VideoDetail(val video: WsVideo, val comments: List<WsComment>)

    suspend fun videoDetail(id: Long): VideoDetail {
        val obj = get("/api/videos/$id")
        val video = parseVideo(obj["video"] as? JsonObject ?: throw WatchSharkException("Not found"))
        val comments = (obj["comments"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseComment(o) } } ?: emptyList()
        return VideoDetail(video, comments)
    }

    suspend fun like(id: Long): Pair<Boolean, Long> {
        val obj = postJson("/api/videos/$id/like", "{}")
        return Pair(obj.optBool("liked"), obj.reqLong("likes"))
    }

    suspend fun comment(id: Long, body: String): Long {
        val obj = postJson("/api/videos/$id/comments", """{"body":${strBody(body)}}""")
        return obj.reqLong("id")
    }

    suspend fun follow(userId: Long): Pair<Boolean, Long> {
        val obj = postJson("/api/follow/$userId", "{}")
        return Pair(obj.optBool("following"), obj.reqLong("followers"))
    }

    data class ChannelDetail(val channel: WsChannel, val videos: List<WsVideo>)

    suspend fun channel(name: String): ChannelDetail {
        val obj = get("/api/channel/$name")
        val u = obj["user"] as? JsonObject ?: throw WatchSharkException("Not found")
        val channel = WsChannel(
            id = u.reqLong("id"),
            username = u.optString("username"),
            avatar = u.strOrNull("avatar"),
            createdAt = u.optString("created_at"),
            followers = u.reqLong("followers"),
            videos = u.reqLong("videos"),
            views = u.reqLong("views"),
            following = u.optBool("following"),
        )
        val videos = (obj["videos"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseVideo(o) } } ?: emptyList()
        return ChannelDetail(channel, videos)
    }

    suspend fun followByName(name: String, want: Boolean): Boolean {
        val detail = channel(name)
        if (detail.channel.following == want) return want
        val (following, _) = follow(detail.channel.id)
        return following
    }

    suspend fun randomWheel(seen: List<Long>): WsVideo? {
        return try {
            val params = if (seen.isEmpty()) emptyMap() else mapOf("seen" to seen.takeLast(128).joinToString(","))
            val obj = get("/api/wheels", params)
            parseVideo(obj["video"] as? JsonObject ?: return null)
        } catch (e: Exception) {
            if (e is WatchSharkException) throw e
            null
        }
    }

    data class WsNotif(
        val id: Long,
        val videoId: Long?,
        val createdAt: String,
        val read: Boolean,
        val title: String,
        val username: String,
        val kind: String,
        val text: String,
    )

    suspend fun notifications(): Pair<List<WsNotif>, Long> {
        val obj = get("/api/notifications")
        val items = (obj["notifications"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            WsNotif(
                id = o.reqLong("id"),
                videoId = (o["video_id"] as? JsonPrimitive)?.longOrNull,
                createdAt = o.optString("created_at"),
                read = o.optBool("read"),
                title = o.optString("title"),
                username = o.optString("username"),
                kind = o.optString("kind"),
                text = o.optString("text"),
            )
        } ?: emptyList()
        return Pair(items, obj.reqLong("unread"))
    }

    suspend fun notifRead(id: Long?) {
        val body = if (id == null) "{}" else """{"id":$id}"""
        try {
            postJson("/api/notifications/read", body)
        } catch (_: Exception) {
        }
    }

    suspend fun uploadVideo(
        title: String,
        description: String,
        kind: String,
        file: File,
        mime: String,
        thumb: File?,
        thumbMime: String?,
    ): Long {
        val boundary = "ws${System.currentTimeMillis()}"
        val bodyBytes = java.io.ByteArrayOutputStream().use { out ->
            fun part(name: String, filename: String?, type: String, bytes: ByteArray) {
                out.write("--$boundary\r\n".toByteArray())
                if (filename != null) {
                    out.write("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n".toByteArray())
                } else {
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n".toByteArray())
                }
                out.write("Content-Type: $type\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n".toByteArray())
            }
            part("title", null, "text/plain", title.toByteArray())
            part("description", null, "text/plain", description.toByteArray())
            part("kind", null, "text/plain", kind.toByteArray())
            part("file", file.name, mime, file.readBytes())
            if (thumb != null) {
                part("thumb", thumb.name, thumbMime ?: "image/jpeg", thumb.readBytes())
            }
            out.write("--$boundary--\r\n".toByteArray())
            out.toByteArray()
        }
        val builder = okhttp3.Request.Builder()
            .url(APP_URL + "/api/videos")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .post(
                bodyBytes.toRequestBody(
                    "multipart/form-data; boundary=$boundary".toMediaType(),
                ),
            )
        sessionToken()?.let { builder.header("Cookie", "ws_token=$it") }
        val client = okhttp3.OkHttpClient()
        client.newCall(builder.build()).execute().use { resp ->
            val text = (resp.body ?: throw WatchSharkException("Upload failed")).string()
            val obj = parseObj(text)
            if (!resp.isSuccessful) throw WatchSharkException("Upload failed")
            return obj.reqLong("id")
        }
    }

    fun formatViews(n: Long): String {
        if (n < 1000) return "$n views"
        val units = listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
        for ((v, s) in units) {
            if (n >= v) {
                val x = n.toDouble() / v
                val t = if (x >= 100) x.toInt().toString() else "%.1f".format(x).trimEnd('0').trimEnd('.')
                return "$t${s} views"
            }
        }
        return "$n views"
    }

    fun relativeTime(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val t = fmt.parse(s.replace('T', ' ').substringBefore('.'))?.time ?: return s
            val sec = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
            when {
                sec < 60 -> "just now"
                sec < 3600 -> "${sec / 60} minutes ago"
                sec < 86400 -> "${sec / 3600} hours ago"
                sec < 86400 * 30 -> "${sec / 86400} days ago"
                else -> "${sec / (86400 * 30)} months ago"
            }
        } catch (e: Exception) {
            s
        }
    }
}
