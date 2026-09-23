package org.watchshark.app.data

import com.google.gson.annotations.SerializedName

data class Video(
    val id: Long = 0,
    val title: String = "",
    val username: String = "",
    @SerializedName("user_id") val userId: Long = 0,
    val src: String = "",
    val thumbnail: String? = null,
    val views: Long = 0,
    var likes: Long = 0,
    var liked: Boolean = false,
    var followers: Long = 0,
    var following: Boolean = false,
    @SerializedName("created_at") val created_at: String = "",
    val avatar: String? = null,
    val kind: String = "",
    val status: String = "",
    val description: String? = null,
    val renditions: Map<String, String>? = null
)

data class MeUser(
    val id: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    val admin: Boolean = false,
    @SerializedName("notify_uploads") val notify_uploads: Boolean = false,
    val banned: Boolean = false,
    @SerializedName("ban_days_left") val ban_days_left: Double = 0.0,
    @SerializedName("ban_reason") val ban_reason: String? = null,
    val deleted: Boolean = false,
    @SerializedName("deleted_reason") val deleted_reason: String? = null
)

data class ChannelUser(
    val id: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    @SerializedName("created_at") val created_at: String? = "",
    var followers: Long = 0,
    val videos: Long = 0,
    val views: Long = 0,
    var following: Boolean = false
)

data class AdminUser(
    val id: Long = 0,
    val username: String = "",
    val verified: Boolean = false,
    val role: String = "",
    @SerializedName("created_at") val created_at: String = "",
    val banned: Boolean = false,
    @SerializedName("ban_days_left") val ban_days_left: Double = 0.0,
    @SerializedName("ban_reason") val ban_reason: String = "",
    @SerializedName("banned_until") val banned_until: Long = 0,
    val deleted: Boolean = false,
    @SerializedName("deleted_reason") val deleted_reason: String = ""
)

data class Notif(
    val id: Long = 0,
    @SerializedName("video_id") val videoId: Long? = null,
    @SerializedName("created_at") val created_at: String = "",
    val read: Boolean = false,
    val title: String = "",
    val username: String = "",
    val kind: String = "",
    val text: String = ""
)

data class Comment(
    val id: Long = 0,
    val body: String = "",
    @SerializedName("created_at") val created_at: String = "",
    val username: String = "",
    val avatar: String? = null
)

data class MeResponse(val user: MeUser?)
data class VideosResponse(
    val videos: List<Video> = emptyList(),
    val page: Long = 1,
    val pages: Long = 0,
    val total: Long = 0
)
data class ChannelResponse(val user: ChannelUser, val videos: List<Video> = emptyList())
data class VideoDetailResponse(val video: Video?, val comments: List<Comment> = emptyList())
data class NotificationsResponse(val notifications: List<Notif> = emptyList(), val unread: Int = 0)
data class AdminUsersResponse(val users: List<AdminUser> = emptyList())
