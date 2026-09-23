package watchshark

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.http.Context

object HandlersCommon {
    fun authUser(ctx: Context): Triple<Long, String, Boolean> {
        val tok = ctx.cookie("ws_token") ?: return Triple(0, "", false)
        if (tok.isEmpty()) return Triple(0, "", false)
        return Auth.verifyToken(tok)
    }

    fun setAuthCookie(ctx: Context, id: Long, username: String) {
        val tok = Auth.issueToken(id, username)
        val maxAge = 7 * 24 * 3600
        ctx.header("Set-Cookie", "ws_token=$tok; Path=/; HttpOnly; SameSite=Lax; Max-Age=$maxAge")
    }

    fun clearAuthCookie(ctx: Context) {
        ctx.header("Set-Cookie", "ws_token=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
    }

    fun readJson(ctx: Context): JsonNode? {
        return try {
            val body = ctx.body()
            if (body.isEmpty()) return null
            Auth.json.readTree(body)
        } catch (_: Exception) {
            null
        }
    }

    fun recordViewLocked(id: Long, viewer: Long, ip: String) {
        // caller holds Db.lock
        Db.conn.prepareStatement("INSERT OR IGNORE INTO video_views (video_id,user_id,ip) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, id)
            ps.setLong(2, viewer)
            ps.setString(3, ip)
            val n = ps.executeUpdate()
            if (n > 0) {
                Db.conn.prepareStatement("UPDATE videos SET views=views+1 WHERE id=?").use { ps2 ->
                    ps2.setLong(1, id)
                    ps2.executeUpdate()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun videoJson(id: Long, viewer: Long): Map<String, Any?>? {
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT v.id,v.user_id,v.title,v.description,v.filename,v.thumbnail,v.mimetype,v.size,v.views,v.created_at,v.status,u.username,u.avatar,v.renditions,v.orientation,v.kind FROM videos v JOIN users u ON u.id=v.user_id WHERE v.id=?"
            ).use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    val vid = rs.getLong(1)
                    val uid = rs.getLong(2)
                    val title = rs.getString(3) ?: ""
                    val desc = rs.getString(4) ?: ""
                    val fn = rs.getString(5) ?: ""
                    val th = rs.getString(6)
                    val mt = rs.getString(7) ?: ""
                    val sz = rs.getLong(8)
                    val views = rs.getLong(9)
                    val ca = rs.getString(10) ?: ""
                    val status = rs.getString(11)
                    val un = rs.getString(12) ?: ""
                    val uav = rs.getString(13)
                    val rend = rs.getString(14)
                    val ori = rs.getString(15)
                    val kk = rs.getString(16)

                    var likes = 0L
                    Db.conn.prepareStatement("SELECT COUNT(*) FROM likes WHERE video_id=?").use { p2 ->
                        p2.setLong(1, vid)
                        p2.executeQuery().use { r2 -> if (r2.next()) likes = r2.getLong(1) }
                    }
                    var liked = false
                    if (viewer >= 0) {
                        Db.conn.prepareStatement("SELECT 1 FROM likes WHERE user_id=? AND video_id=?").use { p2 ->
                            p2.setLong(1, viewer)
                            p2.setLong(2, vid)
                            p2.executeQuery().use { r2 -> liked = r2.next() }
                        }
                    }
                    var comments = 0L
                    Db.conn.prepareStatement("SELECT COUNT(*) FROM comments WHERE video_id=?").use { p2 ->
                        p2.setLong(1, vid)
                        p2.executeQuery().use { r2 -> if (r2.next()) comments = r2.getLong(1) }
                    }
                    var followers = 0L
                    Db.conn.prepareStatement("SELECT COUNT(*) FROM follows WHERE followed_id=?").use { p2 ->
                        p2.setLong(1, uid)
                        p2.executeQuery().use { r2 -> if (r2.next()) followers = r2.getLong(1) }
                    }
                    var following = false
                    if (viewer >= 0 && viewer != uid) {
                        Db.conn.prepareStatement("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?").use { p2 ->
                            p2.setLong(1, viewer)
                            p2.setLong(2, uid)
                            p2.executeQuery().use { r2 -> following = r2.next() }
                        }
                    }
                    val thumb: Any? = if (!th.isNullOrEmpty()) "/t/$th" else null
                    val st = if (!status.isNullOrEmpty()) status else "ready"
                    val oriStr = if (ori == "v") "v" else "h"
                    val kindStr = if (kk == "wheel" || kk == "music") kk else "video"
                    val av: Any? = if (!uav.isNullOrEmpty()) "/a/$uav" else null
                    var renditions: Any? = null
                    if (!rend.isNullOrEmpty()) {
                        try {
                            val m: Map<String, Any> = Auth.json.readValue(rend, Map::class.java) as Map<String, Any>
                            if (m.isNotEmpty()) renditions = m
                        } catch (_: Exception) {}
                    }
                    return mapOf(
                        "id" to vid, "title" to title, "description" to desc,
                        "username" to un, "user_id" to uid,
                        "src" to "/v/$fn", "thumbnail" to thumb,
                        "mimetype" to mt, "size" to sz, "views" to views,
                        "likes" to likes, "liked" to liked, "comments" to comments,
                        "followers" to followers, "following" to following,
                        "created_at" to ca, "status" to st, "avatar" to av,
                        "renditions" to renditions, "orientation" to oriStr, "kind" to kindStr
                    )
                }
            }
        }
    }
}
