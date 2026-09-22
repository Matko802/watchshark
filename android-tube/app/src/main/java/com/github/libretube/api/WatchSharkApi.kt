package com.github.libretube.api

import android.content.Context
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WatchSharkException(message: String) : Exception(message)


object WatchSharkApi {
    const val APP_URL = "https://watchshark.duckdns.org"
    private const val TOKEN_KEY = "ws_session_token"

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private lateinit var appContext: Context

    fun sessionToken(): String? =
        PreferenceHelper.getString(TOKEN_KEY, "").takeIf { it.isNotEmpty() }

    fun saveToken(token: String) = PreferenceHelper.putString(TOKEN_KEY, token)
    fun clearSession() = PreferenceHelper.putString(TOKEN_KEY, "")

    private val cookieSaver = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        response.headers("Set-Cookie").forEach { header ->
            header.split(";").firstOrNull { it.trim().startsWith("ws_token=") }?.let {
                saveToken(it.trim().removePrefix("ws_token="))
            }
        }
        response
    }

    private val cookieSender = Interceptor { chain ->
        val token = sessionToken()
        val request = if (!token.isNullOrEmpty()) {
            chain.request().newBuilder().header("Cookie", "ws_token=$token").build()
        } else chain.request()
        chain.proceed(request)
    }

    private val http = OkHttpClient.Builder()
        .addInterceptor(cookieSender)
        .addInterceptor(cookieSaver)
        .build()

    val api: WatchSharkService by lazy {
        Retrofit.Builder()
            .baseUrl("$APP_URL/")
            .client(http)
            .addConverterFactory(JsonHelper.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WatchSharkService::class.java)
    }

    fun fullUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http")) return path
        return APP_URL + path
    }

    private fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private suspend fun <T> wsCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val msg = try {
                val body = e.response()?.errorBody()?.string()
                val err = body?.let { JsonHelper.json.parseToJsonElement(it) as? JsonObject }
                err?.strOrNull("error")
            } catch (_: Exception) {
                null
            }
            throw WatchSharkException(msg?.takeIf { it.isNotEmpty() } ?: "Request failed (${e.code()})")
        }
    }

    private fun JsonObject.reqLong(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.optString(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.optBool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

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

    suspend fun login(login: String, password: String): Pair<Long, String> = wsCall {
        val obj = api.login(buildJsonObject {
            put("login", login)
            put("password", password)
        })
        obj.strOrNull("error")?.let { throw WatchSharkException(it) }
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
        Pair(user.reqLong("id"), user.optString("username"))
    }

    suspend fun me(): JsonObject? {
        return try {
            val obj = api.me()
            obj["user"] as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
        }
        clearSession()
    }

    data class VideoPage(val videos: List<WsVideo>, val page: Long, val pages: Long)

    suspend fun listVideos(q: String?, sort: String?, page: Long, kind: String?): VideoPage = wsCall {
        val obj = api.videos(q = q, sort = sort, page = page, limit = 12, kind = kind)
        val videos = (obj["videos"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseVideo(o) } } ?: emptyList()
        VideoPage(videos, obj.reqLong("page"), obj.reqLong("pages"))
    }

    data class VideoDetail(val video: WsVideo, val comments: List<WsComment>)

    suspend fun videoDetail(id: Long): VideoDetail = wsCall {
        val obj = api.videoDetail(id)
        val video = parseVideo(obj["video"] as? JsonObject ?: throw WatchSharkException("Not found"))
        val comments = (obj["comments"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseComment(o) } } ?: emptyList()
        VideoDetail(video, comments)
    }

    suspend fun like(id: Long): Pair<Boolean, Long> = wsCall {
        val obj = api.like(id)
        Pair(obj.optBool("liked"), obj.reqLong("likes"))
    }

    suspend fun comment(id: Long, body: String): Long = wsCall {
        val obj = api.comment(id, buildJsonObject { put("body", body) })
        obj.reqLong("id")
    }

    suspend fun follow(userId: Long): Pair<Boolean, Long> = wsCall {
        val obj = api.follow(userId)
        Pair(obj.optBool("following"), obj.reqLong("followers"))
    }

    data class ChannelDetail(val channel: WsChannel, val videos: List<WsVideo>)

    suspend fun channel(name: String): ChannelDetail = wsCall {
        val obj = api.channel(name)
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
        ChannelDetail(channel, videos)
    }

    suspend fun followByName(name: String, want: Boolean): Boolean {
        val detail = channel(name)
        if (detail.channel.following == want) return want
        val (following, _) = follow(detail.channel.id)
        return following
    }

    suspend fun randomWheel(seen: List<Long>): WsVideo? {
        return try {
            val obj = api.wheels(if (seen.isEmpty()) null else seen.takeLast(128).joinToString(","))
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

    suspend fun notifications(): Pair<List<WsNotif>, Long> = wsCall {
        val obj = api.notifications()
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
        Pair(items, obj.reqLong("unread"))
    }

    suspend fun notifRead(id: Long?) {
        try {
            if (id == null) api.notifRead(buildJsonObject {}) else api.notifRead(buildJsonObject { put("id", id) })
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
    ): Long = wsCall {
        val filePart = MultipartBody.Part.createFormData(
            "file", file.name, file.asRequestBody(mime.toMediaType()),
        )
        val thumbPart = thumb?.let {
            MultipartBody.Part.createFormData(
                "thumb", it.name, it.asRequestBody((thumbMime ?: "image/jpeg").toMediaType()),
            )
        }
        val text = "text/plain".toMediaType()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = sessionToken()
                val req: Request = if (!token.isNullOrEmpty()) {
                    chain.request().newBuilder().header("Cookie", "ws_token=$token").build()
                } else chain.request()
                chain.proceed(req)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("$APP_URL/")
            .client(client)
            .addConverterFactory(JsonHelper.json.asConverterFactory("application/json".toMediaType()))
            .build()
        val svc = retrofit.create(UploadService::class.java)
        val obj = svc.upload(
            title.toRequestBody(text),
            description.toRequestBody(text),
            kind.toRequestBody(text),
            filePart,
            thumbPart,
        )
        obj.strOrNull("error")?.let { throw WatchSharkException(it) }
        obj.reqLong("id").takeIf { it > 0 } ?: throw WatchSharkException("Upload failed")
    }

    interface UploadService {
        @retrofit2.http.Multipart
        @POST("api/videos")
        suspend fun upload(
            @retrofit2.http.Part("title") title: okhttp3.RequestBody,
            @retrofit2.http.Part("description") description: okhttp3.RequestBody,
            @retrofit2.http.Part("kind") kind: okhttp3.RequestBody,
            @retrofit2.http.Part file: MultipartBody.Part,
            @retrofit2.http.Part thumb: MultipartBody.Part?,
        ): JsonObject
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
