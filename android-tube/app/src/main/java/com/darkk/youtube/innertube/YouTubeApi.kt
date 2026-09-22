package com.darkk.youtube.innertube

import com.darkk.youtube.innertube.WatchSharkApi.fullUrl

import kotlinx.serialization.Serializable

private fun WatchSharkApi.WsVideo.toItem(): VideoItem = VideoItem(
    videoId = id.toString(),
    title = title.ifBlank { "Untitled" },
    thumbnail = fullUrl(thumbnail) ?: "",
    channelName = username,
    channelAvatar = fullUrl(avatar),
    viewCount = WatchSharkApi.formatViews(views),
    publishedAt = WatchSharkApi.relativeTime(createdAt),
    duration = "",
    isShort = kind == "wheel",
    channelUrl = "@$username"
)

private fun WatchSharkApi.WsVideo.toPlayerData(
    related: List<VideoItem>,
    comments: List<CommentData>,
): PlayerData {
    val quals = mutableListOf<VideoQuality>()
    renditions["720p"]?.let { quals.add(VideoQuality("720p", fullUrl(it) ?: it, null, 720, true)) }
    renditions["480p"]?.let { quals.add(VideoQuality("480p", fullUrl(it) ?: it, null, 480, true)) }
    renditions["360p"]?.let { quals.add(VideoQuality("360p", fullUrl(it) ?: it, null, 360, true)) }
    val sourceUrl = fullUrl(src) ?: src
    val source = VideoQuality("Source", sourceUrl, null, 720, true)
    return PlayerData(
        videoId = id.toString(),
        title = title.ifBlank { "Untitled" },
        author = username,
        thumbnail = fullUrl(thumbnail) ?: "",
        qualities = quals + source,
        defaultQuality = source,
        userAgent = "WatchSharkTube/1.0",
        likes = WatchSharkApi.formatViews(likes),
        views = WatchSharkApi.formatViews(views),
        channelAvatar = fullUrl(avatar),
        channelUrl = "@$username",
        channelSubscribers = "$followers followers",
        relatedVideos = related,
        commentCount = comments.size.toString(),
        comments = comments,
        description = description,
        uploadDate = WatchSharkApi.relativeTime(createdAt),
        userLiked = liked
    )
}

private fun WatchSharkApi.WsComment.toData(): CommentData = CommentData(
    author = username,
    authorAvatar = fullUrl(avatar) ?: "",
    text = body,
    likeCount = "",
    time = WatchSharkApi.relativeTime(createdAt)
)

object YouTubeApi {

    suspend fun getSearchSuggestions(query: String): Result<List<String>> =
        Result.success(emptyList())

    suspend fun getHomeFeed(continuation: String? = null, visitorData: String? = null): Result<HomeFeedPage> = runCatching {
        val page = continuation?.toLongOrNull() ?: 1L
        val res = WatchSharkApi.listVideos(null, "new", page, "video")
        HomeFeedPage(
            videos = res.videos.map { it.toItem() },
            continuationToken = if (page < res.pages) (page + 1).toString() else null,
            visitorData = null
        )
    }

    suspend fun search(query: String, continuation: String? = null, visitorData: String? = null): Result<HomeFeedPage> = runCatching {
        val page = continuation?.toLongOrNull() ?: 1L
        val res = WatchSharkApi.listVideos(query, null, page, null)
        HomeFeedPage(
            videos = res.videos.map { it.toItem() },
            continuationToken = if (page < res.pages) (page + 1).toString() else null,
            visitorData = null
        )
    }

    suspend fun pingPlayback(videoId: String, visitorData: String? = null) {
    }

    suspend fun getNewReleases(): Result<List<ReleaseItem>> = runCatching {
        val res = WatchSharkApi.listVideos(null, "new", 1, "music")
        res.videos.map {
            ReleaseItem(
                id = it.id.toString(),
                title = it.title.ifBlank { "Untitled" },
                artist = it.username,
                thumbnail = fullUrl(it.thumbnail) ?: "",
                isExplicit = false
            )
        }
    }

    suspend fun getPlayerData(videoId: String): Result<PlayerData> = runCatching {
        val id = videoId.toLongOrNull() ?: throw WatchSharkException("Bad video id")
        val detail = WatchSharkApi.videoDetail(id)
        val related = try {
            WatchSharkApi.listVideos(null, "new", 1, null).videos
                .filter { it.id != id }.take(12).map { it.toItem() }
        } catch (e: Exception) {
            emptyList()
        }
        detail.video.toPlayerData(related, detail.comments.map { it.toData() })
    }

    suspend fun getRelatedVideos(videoId: String): Result<List<VideoItem>> = runCatching {
        val id = videoId.toLongOrNull() ?: return@runCatching emptyList()
        WatchSharkApi.listVideos(null, "new", 1, null).videos
            .filter { it.id != id }.take(12).map { it.toItem() }
    }

    suspend fun getChannelDetails(channelUrl: String): Result<ChannelData> = runCatching {
        val name = channelUrl.substringAfter("@").substringAfter("user=").substringBefore("?").substringBefore("/")
        if (name.isBlank()) throw WatchSharkException("Bad channel")
        val detail = WatchSharkApi.channel(name)
        val c = detail.channel
        ChannelData(
            id = c.username,
            name = c.username,
            avatarUrl = fullUrl(c.avatar) ?: "",
            bannerUrl = "",
            subscribers = "${c.followers} followers",
            videoCount = "${c.videos} videos",
            description = "",
            isVerified = false,
            handle = "@${c.username}",
            videos = detail.videos.map { it.toItem() }
        )
    }

    suspend fun getMoreComments(videoId: String, page: Any?): Result<Pair<List<CommentData>, Any?>> =
        Result.success(Pair(emptyList(), null))
}

data class HomeFeedPage(
    val videos: List<VideoItem>,
    val continuationToken: String?,
    val visitorData: String?
)

@Serializable
data class VideoItem(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val channelAvatar: String?,
    val viewCount: String,
    val publishedAt: String,
    val duration: String,
    val isShort: Boolean = false,
    val channelUrl: String = ""
)

data class PlayerData(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val qualities: List<VideoQuality>,
    val defaultQuality: VideoQuality,
    val userAgent: String,
    val likes: String = "",
    val views: String = "",
    val channelAvatar: String? = null,
    val channelUrl: String = "",
    val channelSubscribers: String = "",
    val relatedVideos: List<VideoItem> = emptyList(),
    val commentCount: String = "",
    val comments: List<CommentData> = emptyList(),
    val description: String = "",
    val uploadDate: String = "",
    val tags: List<String> = emptyList(),
    val exactCommentCount: Int = 0,
    val nextCommentsPage: Any? = null,
    val userLiked: Boolean = false
)

data class CommentData(
    val author: String,
    val authorAvatar: String,
    val text: String,
    val likeCount: String,
    val time: String,
    val replyCount: Int = 0
)

data class VideoQuality(
    val label: String,
    val videoUrl: String,
    val audioUrl: String?,
    val height: Int,
    val isCombined: Boolean
)
