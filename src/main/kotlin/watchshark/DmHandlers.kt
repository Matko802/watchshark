package watchshark

import io.javalin.http.Context

object DmHandlers {
    private fun userIdByName(name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        clean.toLongOrNull()?.let { if (it > 0) return it }
        val like = clean.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        if (like.isEmpty()) return null
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id FROM users WHERE lower(username)=?").use { ps ->
                ps.setString(1, like.lowercase())
                ps.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    private fun resolveTarget(to: String): Triple<Long, String, Boolean> {
        val rid = userIdByName(to) ?: return Triple(0, "", false)
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT username,deleted FROM users WHERE id=?").use { ps ->
                ps.setLong(1, rid)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return Triple(0, "", false)
                    val un = rs.getString(1) ?: ""
                    if (rs.getInt(2) != 0) return Triple(0, "", false)
                    return Triple(rid, un, true)
                }
            }
        }
    }

    /** True when a and b follow each other (friends). Caller must hold Db.lock. */
    private fun isMutualLocked(a: Long, b: Long): Boolean {
        if (a == b) return false
        Db.conn.prepareStatement(
            "SELECT 1 FROM follows f1 WHERE f1.follower_id=? AND f1.followed_id=? " +
                "AND EXISTS (SELECT 1 FROM follows f2 WHERE f2.follower_id=? AND f2.followed_id=?)"
        ).use { ps ->
            ps.setLong(1, a); ps.setLong(2, b); ps.setLong(3, b); ps.setLong(4, a)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    /** All mutual-friend ids of uid. Caller must hold Db.lock. */
    private fun mutualIdsLocked(uid: Long): Set<Long> {
        val following = mutableSetOf<Long>()
        Db.conn.prepareStatement("SELECT followed_id FROM follows WHERE follower_id=?").use { ps ->
            ps.setLong(1, uid)
            ps.executeQuery().use { rs -> while (rs.next()) following.add(rs.getLong(1)) }
        }
        if (following.isEmpty()) return emptySet()
        val followers = mutableSetOf<Long>()
        Db.conn.prepareStatement("SELECT follower_id FROM follows WHERE followed_id=?").use { ps ->
            ps.setLong(1, uid)
            ps.executeQuery().use { rs -> while (rs.next()) followers.add(rs.getLong(1)) }
        }
        following.retainAll(followers)
        return following
    }

    private fun previewOf(body: String): String {
        var preview = body.trim().replace(Regex("\\s+"), " ")
        if (preview.codePointCount(0, preview.length) > 120) {
            preview = HttpUtil.truncateRunes(preview, 120)
        }
        return preview
    }

    fun friends(ctx: Context, uid: Long) {
        HttpUtil.writeJson(ctx, 200, mapOf("friends" to friendsList(uid)))
    }

    fun sync(ctx: Context, uid: Long) {
        HttpUtil.writeJson(ctx, 200, mapOf("friends" to friendsList(uid), "unread" to unreadTotal(uid)))
    }

    private fun friendsList(uid: Long): List<Map<String, Any?>> {
        val ids: Set<Long>
        synchronized(Db.lock) {
            ids = mutualIdsLocked(uid)
        }
        if (ids.isEmpty()) {
            return emptyList()
        }
        // last message id per friend (either direction)
        val lastIds = mutableMapOf<Long, Long>()
        val placeholders = ids.joinToString(",") { "?" }
        val idList = ids.toList()
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS peer, MAX(id) AS last_id " +
                    "FROM dm_messages WHERE (sender_id=? AND recipient_id IN ($placeholders)) " +
                    "OR (recipient_id=? AND sender_id IN ($placeholders)) GROUP BY peer"
            ).use { ps ->
                var i = 1
                ps.setLong(i++, uid)
                ps.setLong(i++, uid)
                for (id in idList) ps.setLong(i++, id)
                ps.setLong(i++, uid)
                for (id in idList) ps.setLong(i++, id)
                ps.executeQuery().use { rs ->
                    while (rs.next()) lastIds[rs.getLong(1)] = rs.getLong(2)
                }
            }
        }
        val out = mutableListOf<Map<String, Any?>>()
        for (fid in idList) {
            var username = ""
            var avatar: String? = null
            var lastSeen = 0L
            var deleted = false
            synchronized(Db.lock) {
                Db.conn.prepareStatement("SELECT username,avatar,last_seen,deleted FROM users WHERE id=?").use { ps ->
                    ps.setLong(1, fid)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            username = rs.getString(1) ?: ""
                            avatar = rs.getString(2)
                            lastSeen = try { rs.getLong(3) } catch (_: Exception) { 0L }
                            deleted = rs.getInt(4) != 0
                        }
                    }
                }
            }
            if (username.isEmpty() || deleted) continue
            val lastId = lastIds[fid] ?: 0L
            var lastBody = ""
            var lastAt = ""
            var lastSender = 0L
            var unread = 0L
            if (lastId > 0) {
                synchronized(Db.lock) {
                    Db.conn.prepareStatement("SELECT sender_id,body,created_at FROM dm_messages WHERE id=?").use { ps ->
                        ps.setLong(1, lastId)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                lastSender = rs.getLong(1)
                                lastBody = rs.getString(2) ?: ""
                                lastAt = rs.getString(3) ?: ""
                            }
                        }
                    }
                    Db.conn.prepareStatement("SELECT COUNT(*) FROM dm_messages WHERE sender_id=? AND recipient_id=? AND read=0").use { ps ->
                        ps.setLong(1, fid)
                        ps.setLong(2, uid)
                        ps.executeQuery().use { rs -> if (rs.next()) unread = rs.getLong(1) }
                    }
                }
            }
            out.add(
                mapOf(
                    "user_id" to fid,
                    "username" to username,
                    "avatar" to if (!avatar.isNullOrEmpty()) "/a/$avatar" else null,
                    "online" to Db.isOnline(lastSeen),
                    "last_message" to previewOf(lastBody),
                    "last_message_id" to lastId,
                    "last_at" to lastAt,
                    "last_sender_id" to lastSender,
                    "unread" to unread
                )
            )
        }
        out.sortWith(compareByDescending<Map<String, Any?>> { (it["last_message_id"] as? Number)?.toLong() ?: 0L }.thenBy { ((it["username"] as? String) ?: "").lowercase() })
        return out
    }

    private fun unreadTotal(uid: Long): Long {
        var unread = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT COUNT(*) FROM dm_messages m WHERE m.recipient_id=? AND m.read=0 " +
                    "AND EXISTS (SELECT 1 FROM follows f1 WHERE f1.follower_id=m.recipient_id AND f1.followed_id=m.sender_id) " +
                    "AND EXISTS (SELECT 1 FROM follows f2 WHERE f2.follower_id=m.sender_id AND f2.followed_id=m.recipient_id)"
            ).use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) unread = rs.getLong(1) }
            }
        }
        return unread
    }

    fun send(ctx: Context, uid: Long) {
        val st = Auth.getBanState(uid)
        if (st.deleted) {
            HttpUtil.writeErr(ctx, 403, "Account deleted")
            return
        }
        if (st.banned) {
            HttpUtil.writeErr(ctx, 403, "You are banned")
            return
        }
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val to = node.get("to")?.asText("") ?: ""
        var body = node.get("body")?.asText("") ?: ""
        body = HttpUtil.truncateRunes(body, 2000)
        if (to.trim().isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "No recipient")
            return
        }
        if (body.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "Empty message")
            return
        }
        val (rid, _, ok) = resolveTarget(to)
        if (!ok) {
            HttpUtil.writeErr(ctx, 404, "No such user")
            return
        }
        if (rid == uid) {
            HttpUtil.writeErr(ctx, 400, "Cannot message yourself")
            return
        }
        val mutual = synchronized(Db.lock) { isMutualLocked(uid, rid) }
        if (!mutual) {
            HttpUtil.writeErr(ctx, 403, "Friends only — follow each other to chat")
            return
        }
        var id = 0L
        var createdAt = ""
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "INSERT INTO dm_messages (sender_id,recipient_id,body,read) VALUES (?,?,?,0)"
            ).use { ps ->
                ps.setLong(1, uid)
                ps.setLong(2, rid)
                ps.setString(3, body)
                ps.executeUpdate()
            }
            id = Db.lastInsertId()
            Db.conn.prepareStatement("SELECT created_at FROM dm_messages WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) createdAt = rs.getString(1) ?: "" }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "id" to id, "created_at" to createdAt))
    }

    fun conversations(ctx: Context, uid: Long) {
        data class Peer(val peerId: Long, val lastId: Long)
        val peers = mutableListOf<Peer>()
        val mutuals: Set<Long>
        synchronized(Db.lock) {
            mutuals = mutualIdsLocked(uid)
            Db.conn.prepareStatement(
                "SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS peer, MAX(id) AS last_id " +
                    "FROM dm_messages WHERE sender_id=? OR recipient_id=? GROUP BY peer ORDER BY last_id DESC LIMIT 50"
            ).use { ps ->
                ps.setLong(1, uid)
                ps.setLong(2, uid)
                ps.setLong(3, uid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val peer = rs.getLong(1)
                        if (mutuals.contains(peer)) peers.add(Peer(peer, rs.getLong(2)))
                    }
                }
            }
        }
        val out = mutableListOf<Map<String, Any?>>()
        for (p in peers) {
            var username = ""
            var avatar: String? = null
            var lastSeen = 0L
            var lastBody = ""
            var lastAt = ""
            var lastSender = 0L
            var unread = 0L
            synchronized(Db.lock) {
                Db.conn.prepareStatement("SELECT username,avatar,last_seen FROM users WHERE id=?").use { ps ->
                    ps.setLong(1, p.peerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            username = rs.getString(1) ?: ""
                            avatar = rs.getString(2)
                            lastSeen = try { rs.getLong(3) } catch (_: Exception) { 0L }
                        }
                    }
                }
                if (username.isEmpty()) continue
                Db.conn.prepareStatement("SELECT sender_id,body,created_at FROM dm_messages WHERE id=?").use { ps ->
                    ps.setLong(1, p.lastId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            lastSender = rs.getLong(1)
                            lastBody = rs.getString(2) ?: ""
                            lastAt = rs.getString(3) ?: ""
                        }
                    }
                }
                Db.conn.prepareStatement("SELECT COUNT(*) FROM dm_messages WHERE sender_id=? AND recipient_id=? AND read=0").use { ps ->
                    ps.setLong(1, p.peerId)
                    ps.setLong(2, uid)
                    ps.executeQuery().use { rs -> if (rs.next()) unread = rs.getLong(1) }
                }
            }
            val av: Any? = if (!avatar.isNullOrEmpty()) "/a/$avatar" else null
            var preview = lastBody.trim().replace(Regex("\\s+"), " ")
            if (preview.codePointCount(0, preview.length) > 120) {
                preview = HttpUtil.truncateRunes(preview, 120)
            }
            out.add(
                mapOf(
                    "user_id" to p.peerId,
                    "username" to username,
                    "avatar" to av,
                    "online" to Db.isOnline(lastSeen),
                    "last_message" to preview,
                    "last_message_id" to p.lastId,
                    "last_at" to lastAt,
                    "last_sender_id" to lastSender,
                    "unread" to unread
                )
            )
        }
        HttpUtil.writeJson(ctx, 200, mapOf("conversations" to out))
    }

    fun thread(ctx: Context, uid: Long) {
        val peerParam = ctx.queryParam("user") ?: ""
        val after = ctx.queryParam("after_id")?.toLongOrNull() ?: 0
        val before = ctx.queryParam("before_id")?.toLongOrNull() ?: 0
        var limit = ctx.queryParam("limit")?.toIntOrNull() ?: 30
        if (limit < 1) limit = 30
        if (limit > 100) limit = 100
        val (pid, pun, ok) = resolveTarget(peerParam)
        if (!ok) {
            HttpUtil.writeErr(ctx, 404, "No such user")
            return
        }
        val mutual = synchronized(Db.lock) { isMutualLocked(uid, pid) }
        if (!mutual) {
            HttpUtil.writeErr(ctx, 403, "Friends only — follow each other to chat")
            return
        }
        val out = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            val sql = when {
                after > 0 -> "SELECT id,sender_id,recipient_id,body,created_at,read FROM dm_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) AND id>? ORDER BY id ASC LIMIT ?"
                before > 0 -> "SELECT id,sender_id,recipient_id,body,created_at,read FROM dm_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) AND id<? ORDER BY id DESC LIMIT ?"
                else -> "SELECT id,sender_id,recipient_id,body,created_at,read FROM dm_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) ORDER BY id DESC LIMIT ?"
            }
            Db.conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, uid); ps.setLong(2, pid); ps.setLong(3, pid); ps.setLong(4, uid)
                if (after > 0) {
                    ps.setLong(5, after); ps.setInt(6, limit)
                } else if (before > 0) {
                    ps.setLong(5, before); ps.setInt(6, limit)
                } else {
                    ps.setInt(5, limit)
                }
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        out.add(
                            mapOf(
                                "id" to rs.getLong(1),
                                "sender_id" to rs.getLong(2),
                                "recipient_id" to rs.getLong(3),
                                "body" to (rs.getString(4) ?: ""),
                                "created_at" to (rs.getString(5) ?: ""),
                                "read" to (rs.getInt(6) != 0)
                            )
                        )
                    }
                }
            }
        }
        if (before > 0 || (after <= 0 && before <= 0)) out.reverse()
        var peerAvatar: String? = null
        var peerOnline = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT avatar,last_seen FROM users WHERE id=?").use { ps ->
                ps.setLong(1, pid)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        peerAvatar = rs.getString(1)
                        peerOnline = Db.isOnline(try { rs.getLong(2) } catch (_: Exception) { 0L })
                    }
                }
            }
        }
        val av: Any? = if (!peerAvatar.isNullOrEmpty()) "/a/$peerAvatar" else null
        HttpUtil.writeJson(
            ctx, 200, mapOf(
                "messages" to out,
                "peer" to mapOf("user_id" to pid, "username" to pun, "avatar" to av, "online" to peerOnline),
                "me" to uid
            )
        )
    }

    fun markRead(ctx: Context, uid: Long) {
        val node = HandlersCommon.readJson(ctx)
        val userParam = node?.get("user")?.takeUnless { it.isNull }?.asText() ?: ctx.queryParam("user") ?: ""
        synchronized(Db.lock) {
            if (userParam.trim().isEmpty()) {
                Db.conn.prepareStatement("UPDATE dm_messages SET read=1 WHERE recipient_id=? AND read=0").use { ps ->
                    ps.setLong(1, uid)
                    ps.executeUpdate()
                }
            } else {
                val pid = userIdByName(userParam) ?: 0L
                if (pid > 0) {
                    Db.conn.prepareStatement("UPDATE dm_messages SET read=1 WHERE sender_id=? AND recipient_id=? AND read=0").use { ps ->
                        ps.setLong(1, pid)
                        ps.setLong(2, uid)
                        ps.executeUpdate()
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "unread" to unreadTotal(uid)))
    }

    fun unread(ctx: Context, uid: Long) {
        HttpUtil.writeJson(ctx, 200, mapOf("unread" to unreadTotal(uid)))
    }

    fun userSearch(ctx: Context, uid: Long) {
        var q = (ctx.queryParam("q") ?: "").trim()
        if (q.codePointCount(0, q.length) > 30) q = HttpUtil.truncateRunes(q, 30)
        if (q.isEmpty()) {
            HttpUtil.writeJson(ctx, 200, mapOf("users" to listOf<Any>()))
            return
        }
        val like = "%${HttpUtil.escapeLike(q.lowercase())}%"
        val out = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            // friends only: must follow each other
            Db.conn.prepareStatement(
                "SELECT u.id,u.username,u.avatar,u.last_seen FROM users u " +
                    "WHERE lower(u.username) LIKE ? ESCAPE '\\' AND u.id!=? AND u.deleted=0 " +
                    "AND EXISTS (SELECT 1 FROM follows f1 WHERE f1.follower_id=? AND f1.followed_id=u.id) " +
                    "AND EXISTS (SELECT 1 FROM follows f2 WHERE f2.follower_id=u.id AND f2.followed_id=?) " +
                    "ORDER BY u.username LIMIT 10"
            ).use { ps ->
                ps.setString(1, like)
                ps.setLong(2, uid)
                ps.setLong(3, uid)
                ps.setLong(4, uid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val av = rs.getString(3)
                        out.add(
                            mapOf(
                                "user_id" to rs.getLong(1),
                                "username" to (rs.getString(2) ?: ""),
                                "avatar" to if (!av.isNullOrEmpty()) "/a/$av" else null,
                                "online" to Db.isOnline(try { rs.getLong(4) } catch (_: Exception) { 0L })
                            )
                        )
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("users" to out))
    }
}
