package com.github.libretube.api

import com.github.libretube.api.WatchSharkBackend.api
import com.github.libretube.api.WatchSharkBackend.fullUrl
import com.github.libretube.api.obj.Channel
import com.github.libretube.api.obj.ChannelTab
import com.github.libretube.api.obj.ChannelTabResponse
import com.github.libretube.api.obj.Comment
import com.github.libretube.api.obj.CommentsPage
import com.github.libretube.api.obj.ContentItem
import com.github.libretube.api.obj.DeArrowContent
import com.github.libretube.api.obj.PipedStream
import com.github.libretube.api.obj.Playlist
import com.github.libretube.api.obj.SearchResult
import com.github.libretube.api.obj.SegmentData
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.api.obj.Streams
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun JsonObject.str(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""

private fun JsonObject.num(key: String): Long =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject.flag(key: String): Boolean =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private suspend fun <T> wsCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val msg = try {
            val body = e.response()?.errorBody()?.string()
            val err = body?.let { JsonHelper.json.parseToJsonElement(it) as? JsonObject }
            err?.str("error")
        } catch (_: Exception) {
            null
        }
        throw Exception(msg?.takeIf { it.isNotEmpty() } ?: "Request failed (${e.code()})")
    }
}

private fun parseTime(s: String): Long {
    if (s.isBlank()) return 0
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fmt.parse(s.replace('T', ' ').substringBefore('.'))?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

private fun formatViews(n: Long): String {
    if (n < 1000) return "$n"
    val units = listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    for ((v, s) in units) {
        if (n >= v) {
            val x = n.toDouble() / v
            return (if (x >= 100) x.toInt().toString() else "%.1f".format(x).trimEnd('0').trimEnd('.')) + s
        }
    }
    return "$n"
}

private fun wsVideoToStreamItem(o: JsonObject): StreamItem? {
    val id = o.num("id")
    if (id <= 0) return null
    val username = o.str("username")
    val kind = o.str("kind").ifEmpty { "video" }
    return StreamItem(
        url = id.toString(),
        type = StreamItem.TYPE_STREAM,
        title = o.str("title").ifBlank { "Untitled" },
        thumbnail = fullUrl((o["thumbnail"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull),
        uploaderName = username,
        uploaderUrl = "@$username",
        uploaderAvatar = fullUrl((o["avatar"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull),
        uploadedDate = o.str("created_at"),
        duration = -1,
        views = o.num("views"),
        uploaderVerified = false,
        uploaded = parseTime(o.str("created_at")),
        shortDescription = o.str("description"),
        isShort = kind == "wheel"
    )
}

private fun wsVideoToStreams(o: JsonObject, related: List<StreamItem>, comments: List<Comment>): Streams {
    val id = o.num("id")
    val username = o.str("username")
    val renditions = (o["renditions"] as? JsonObject)
    fun renditionUrl(key: String): String? {
        val v = (renditions?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return fullUrl(v)
    }
    val videoStreams = mutableListOf<PipedStream>()
    renditionUrl("720p")?.let {
        videoStreams.add(PipedStream(url = it, format = "WEBM", quality = "720p", mimeType = "video/webm", codec = "av01", width = 1280, height = 720))
    }
    renditionUrl("480p")?.let {
        videoStreams.add(PipedStream(url = it, format = "WEBM", quality = "480p", mimeType = "video/webm", codec = "av01", width = 854, height = 480))
    }
    renditionUrl("360p")?.let {
        videoStreams.add(PipedStream(url = it, format = "WEBM", quality = "360p", mimeType = "video/webm", codec = "av01", width = 640, height = 360))
    }
    val sourceUrl = fullUrl(o.str("src")) ?: ""
    val source = PipedStream(url = sourceUrl, format = "WEBM", quality = "Source", mimeType = "video/webm", codec = "av01", width = 1280, height = 720)
    videoStreams.add(source)
    val audioStreams = mutableListOf<PipedStream>()
    if (o.str("mimetype").startsWith("audio")) {
        audioStreams.add(PipedStream(url = sourceUrl, format = "WEBM", quality = "Audio", mimeType = "audio/webm", codec = "opus"))
    }
    return Streams(
        title = o.str("title").ifBlank { "Untitled" },
        description = o.str("description"),
        uploader = username,
        uploaderUrl = "@$username",
        uploaderAvatar = fullUrl((o["avatar"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull),
        thumbnailUrl = fullUrl((o["thumbnail"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull) ?: "",
        category = o.str("kind").ifEmpty { "video" },
        uploaderVerified = false,
        duration = 1,
        views = o.num("views"),
        likes = o.num("likes"),
        dislikes = 0,
        audioStreams = audioStreams,
        videoStreams = videoStreams,
        relatedStreams = related,
        uploaderSubscriberCount = o.num("followers"),
        isShort = o.str("kind") == "wheel"
    )
}

open class WatchSharkMediaServiceRepository : MediaServiceRepository {
    override fun getTrendingCategories(): List<TrendingCategory> = emptyList()

    private suspend fun listPage(q: String?, sort: String?, page: Long, kind: String?): Triple<List<StreamItem>, Long, Long> {
        val obj = api.videos(q = q, sort = sort, page = page, limit = 24, kind = kind)
        val items = (obj["videos"] as? JsonArray)?.mapNotNull {
            (it as? JsonObject)?.let { o -> wsVideoToStreamItem(o) }
        } ?: emptyList()
        return Triple(items, (obj["page"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: page, (obj["pages"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: page)
    }

    override suspend fun getTrending(region: String, category: TrendingCategory): List<StreamItem> = wsCall {
        val kind = if (category == TrendingCategory.MUSIC) "music" else null
        listPage(null, "popular", 1, kind).first
    }

    override suspend fun getStreams(videoId: String): Streams = wsCall {
        val id = videoId.toLongOrNull() ?: throw Exception("Bad video id")
        val obj = api.videoDetail(id)
        val video = obj["video"] as? JsonObject ?: throw Exception("Not found")
        val related = try {
            listPage(null, "new", 1, null).first.filter { it.url != id.toString() }.take(12)
        } catch (e: Exception) {
            emptyList()
        }
        val comments = (obj["comments"] as? JsonArray)?.mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            Comment(
                author = o.str("username"),
                commentId = o.num("id").toString(),
                commentText = o.str("body"),
                commentedTime = o.str("created_at"),
                commentorUrl = "@${o.str("username")}",
                hearted = false,
                likeCount = 0,
                pinned = false,
                thumbnail = fullUrl((o["avatar"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull) ?: "",
                verified = false,
                replyCount = 0
            )
        } ?: emptyList()
        wsVideoToStreams(video, related, comments)
    }

    override suspend fun getComments(videoId: String): CommentsPage = wsCall {
        val id = videoId.toLongOrNull() ?: throw Exception("Bad video id")
        val obj = api.videoDetail(id)
        val comments = (obj["comments"] as? JsonArray)?.mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            Comment(
                author = o.str("username"),
                commentId = o.num("id").toString(),
                commentText = o.str("body"),
                commentedTime = o.str("created_at"),
                commentorUrl = "@${o.str("username")}",
                hearted = false,
                likeCount = 0,
                pinned = false,
                thumbnail = fullUrl((o["avatar"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull) ?: "",
                verified = false,
                replyCount = 0
            )
        } ?: emptyList()
        CommentsPage(comments = comments, commentCount = comments.size.toLong())
    }

    override suspend fun getSegments(videoId: String, category: List<String>, actionType: List<String>?): SegmentData =
        SegmentData()

    override suspend fun getDeArrowContent(videoId: String): DeArrowContent? = null

    override suspend fun getCommentsNextPage(videoId: String, nextPage: String): CommentsPage =
        CommentsPage()

    override suspend fun getSearchResults(searchQuery: String, filter: String): SearchResult = wsCall {
        val (items, page, pages) = listPage(searchQuery, null, 1, null)
        SearchResult(
            items = items.map {
                ContentItem(
                    url = it.url ?: "",
                    type = StreamItem.TYPE_STREAM,
                    thumbnail = it.thumbnail ?: "",
                    title = it.title,
                    uploaderUrl = it.uploaderUrl,
                    uploaderAvatar = it.uploaderAvatar,
                    uploaderName = it.uploaderName,
                    uploaded = it.uploaded
                )
            },
            nextpage = if (pages > 1) "2" else null
        )
    }

    override suspend fun getSearchResultsNextPage(searchQuery: String, filter: String, nextPage: String): SearchResult = wsCall {
        val page = nextPage.toLongOrNull() ?: 1L
        val (items, _, pages) = listPage(searchQuery, null, page, null)
        SearchResult(
            items = items.map {
                ContentItem(
                    url = it.url ?: "",
                    type = StreamItem.TYPE_STREAM,
                    thumbnail = it.thumbnail ?: "",
                    title = it.title,
                    uploaderUrl = it.uploaderUrl,
                    uploaderAvatar = it.uploaderAvatar,
                    uploaderName = it.uploaderName,
                    uploaded = it.uploaded
                )
            },
            nextpage = if (page < pages) (page + 1).toString() else null
        )
    }

    override suspend fun getSuggestions(query: String): List<String> = emptyList()

    private fun channelToModel(u: JsonObject, videos: List<StreamItem>): Channel {
        val username = u.str("username")
        return Channel(
            id = "@$username",
            name = username,
            avatarUrl = fullUrl((u["avatar"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull),
            bannerUrl = null,
            description = "",
            subscriberCount = u.num("followers"),
            verified = false,
            relatedStreams = videos
        )
    }

    override suspend fun getChannel(channelId: String): Channel = wsCall {
        val name = channelId.removePrefix("@").substringBefore("?").substringAfter("user=").substringBefore("/")
        val obj = api.channel(name.ifBlank { throw Exception("Bad channel") })
        val u = obj["user"] as? JsonObject ?: throw Exception("Not found")
        val videos = (obj["videos"] as? JsonArray)?.mapNotNull {
            (it as? JsonObject)?.let { o -> wsVideoToStreamItem(o) }
        } ?: emptyList()
        channelToModel(u, videos)
    }

    override suspend fun getChannelTab(data: String, nextPage: String?): ChannelTabResponse {
        return ChannelTabResponse()
    }

    override suspend fun getChannelByName(channelName: String): Channel = getChannel(channelName)

    override suspend fun getChannelNextPage(channelId: String, nextPage: String): Channel = wsCall {
        getChannel(channelId)
    }

    override suspend fun getPlaylist(playlistId: String): Playlist = Playlist()

    override suspend fun getPlaylistNextPage(playlistId: String, nextPage: String): Playlist = Playlist()
}
