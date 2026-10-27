package watchshark.duckdns.org.data
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class Video(
    val id: Long = 0,
    val title: String = "",
    val username: String = "",
    @SerialName("user_id") val userId: Long = 0,
    val src: String = "",
    val thumbnail: String? = null,
    val views: Long = 0,
    var likes: Long = 0,
    var liked: Boolean = false,
    var followers: Long = 0,
    var following: Boolean = false,
    @SerialName("created_at") val created_at: String = "",
    val avatar: String? = null,
    val kind: String = "",
    val status: String = "",
    val description: String? = null,
    val renditions: Map<String, String>? = null,
    val orientation: String? = null
)
@Serializable
data class MeUser(
    val id: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    val admin: Boolean = false,
    @SerialName("notify_uploads") val notify_uploads: Boolean = false,
    val banned: Boolean = false,
    @SerialName("ban_days_left") val ban_days_left: Double = 0.0,
    @SerialName("ban_reason") val ban_reason: String? = null,
    val deleted: Boolean = false,
    @SerialName("deleted_reason") val deleted_reason: String? = null,
    val online: Boolean = false
)
@Serializable
data class ChannelUser(
    val id: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    @SerialName("created_at") val created_at: String? = "",
    var followers: Long = 0,
    val videos: Long = 0,
    val views: Long = 0,
    var following: Boolean = false,
    val online: Boolean = false,
    @SerialName("counts") val counts: Map<String, Long> = emptyMap()
)
@Serializable
data class AdminUser(
    val id: Long = 0,
    val username: String = "",
    val verified: Boolean = false,
    val role: String = "",
    @SerialName("created_at") val created_at: String = "",
    val banned: Boolean = false,
    @SerialName("ban_days_left") val ban_days_left: Double = 0.0,
    @SerialName("ban_reason") val ban_reason: String = "",
    @SerialName("banned_until") val banned_until: Long = 0,
    val deleted: Boolean = false,
    @SerialName("deleted_reason") val deleted_reason: String = "",
    val avatar: String? = null,
    val online: Boolean = false
)
@Serializable
data class Notif(
    val id: Long = 0,
    @SerialName("video_id") val videoId: Long? = null,
    @SerialName("created_at") val created_at: String = "",
    val read: Boolean = false,
    val title: String = "",
    val username: String = "",
    val kind: String = "",
    val text: String = ""
)
@Serializable
data class Comment(
    val id: Long = 0,
    val body: String = "",
    @SerialName("created_at") val created_at: String = "",
    val username: String = "",
    val avatar: String? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("parent_username") val parentUsername: String? = null,
    val online: Boolean = false
)
@Serializable
data class MeResponse(val user: MeUser?)
@Serializable
data class VideosResponse(
    val videos: List<Video>? = null,
    val page: Long = 1,
    val pages: Long = 0,
    val total: Long = 0
)
@Serializable
data class ChannelResponse(val user: ChannelUser, val videos: List<Video>? = null)
@Serializable
data class VideoDetailResponse(val video: Video?, val comments: List<Comment>? = null)
@Serializable
data class NotificationsResponse(val notifications: List<Notif>? = null, val unread: Int = 0)
@Serializable
data class AdminUsersResponse(val users: List<AdminUser>? = null)
@Serializable
data class DmConversation(
    @SerialName("user_id") val userId: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    val online: Boolean = false,
    @SerialName("last_message") val lastMessage: String = "",
    @SerialName("last_message_id") val lastMessageId: Long = 0,
    @SerialName("last_at") val lastAt: String = "",
    @SerialName("last_sender_id") val lastSenderId: Long = 0,
    val unread: Long = 0
)
@Serializable
data class DmMessage(
    val id: Long = 0,
    @SerialName("sender_id") val senderId: Long = 0,
    @SerialName("recipient_id") val recipientId: Long = 0,
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val read: Boolean = false
)
@Serializable
data class DmPeer(
    @SerialName("user_id") val userId: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    val online: Boolean = false
)
@Serializable
data class ConversationsResponse(val conversations: List<DmConversation>? = null)
@Serializable
data class DmThreadResponse(
    val messages: List<DmMessage>? = null,
    val peer: DmPeer? = null,
    val me: Long = 0
)
@Serializable
data class DmUnreadResponse(val unread: Long = 0)
@Serializable
data class DmUserEntry(
    @SerialName("user_id") val userId: Long = 0,
    val username: String = "",
    val avatar: String? = null,
    val online: Boolean = false
)
@Serializable
data class DmUserSearchResponse(val users: List<DmUserEntry>? = null)