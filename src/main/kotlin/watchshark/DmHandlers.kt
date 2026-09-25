package watchshark

object DmHandlers {
    private fun isFriend(a: Long, b: Long): Boolean {
        if (a == b) return false
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT 1 FROM follows WHERE follower_id=? AND followed_id=? " +
                    "AND EXISTS (SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?)"
            ).use { ps ->
                ps.setLong(1, a); ps.setLong(2, b); ps.setLong(3, b); ps.setLong(4, a)
                ps.executeQuery().use { rs -> return rs.next() }
            }
        }
    }

    private fun userIdByName(name: String): Long? {
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id FROM users WHERE lower(username)=?").use { ps ->
                ps.setString(1, name.lowercase())
                ps.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1) else null }
            }
        }
    }

    fun setKey(ctx: io.javalin.http.Context, uid: Long) {
        var pub = ""
        try {
            val n = Auth.json.readTree(ctx.body())
            pub = n.get("pubkey")?.asText("") ?: ""
        } catch (_: Exception) {}
        pub = pub.trim()
        if (pub.length < 40 || pub.length > 200) {
            HttpUtil.writeErr(ctx, 400, "Bad key")
            return
        }
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "INSERT INTO dm_keys (user_id,pubkey) VALUES (?,?) " +
                    "ON CONFLICT(user_id) DO UPDATE SET pubkey=excluded.pubkey"
            ).use { ps ->
                ps.setLong(1, uid); ps.setString(2, pub)
                ps.executeUpdate()
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun getKey(ctx: io.javalin.http.Context, name: String) {
        if (name.isEmpty()) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val clean = name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        if (clean.isEmpty()) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        var un = ""
        var pub: String? = null
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT u.username,k.pubkey FROM users u LEFT JOIN dm_keys k ON k.user_id=u.id " +
                    "WHERE lower(u.username)=?"
            ).use { ps ->
                ps.setString(1, clean.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        un = rs.getString(1) ?: ""
                        pub = rs.getString(2)
                    }
                }
            }
        }
        if (un.isEmpty() || pub.isNullOrEmpty()) {
            HttpUtil.writeErr(ctx, 404, "No key yet")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("username" to un, "pubkey" to pub))
    }

    fun friends(ctx: io.javalin.http.Context, uid: Long) {
        val out = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT u.id,u.username,u.avatar FROM users u " +
                    "JOIN follows f1 ON f1.followed_id=u.id AND f1.follower_id=? " +
                    "JOIN follows f2 ON f2.follower_id=u.id AND f2.followed_id=? " +
                    "ORDER BY u.username"
            ).use { ps ->
                ps.setLong(1, uid); ps.setLong(2, uid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val av = rs.getString(3)
                        out.add(
                            mapOf(
                                "id" to rs.getLong(1),
                                "username" to (rs.getString(2) ?: ""),
                                "avatar" to if (!av.isNullOrEmpty()) "/a/$av" else null
                            )
                        )
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("friends" to out))
    }

    fun send(ctx: io.javalin.http.Context, uid: Long) {
        var to = ""
        var nonce = ""
        var body = ""
        try {
            val n = Auth.json.readTree(ctx.body())
            to = n.get("to")?.asText("") ?: ""
            nonce = n.get("nonce")?.asText("") ?: ""
            body = n.get("body")?.asText("") ?: ""
        } catch (_: Exception) {}
        val rid = to.toLongOrNull() ?: userIdByName(to.trim())
        if (rid == null || rid <= 0) {
            HttpUtil.writeErr(ctx, 404, "No such user")
            return
        }
        if (!isFriend(uid, rid)) {
            HttpUtil.writeErr(ctx, 403, "Not friends")
            return
        }
        if (nonce.length < 8 || nonce.length > 100 || body.isEmpty() || body.length > 16384) {
            HttpUtil.writeErr(ctx, 400, "Bad message")
            return
        }
        var id = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "INSERT INTO dm_messages (sender_id,recipient_id,nonce,body) VALUES (?,?,?,?)"
            ).use { ps ->
                ps.setLong(1, uid); ps.setLong(2, rid)
                ps.setString(3, nonce); ps.setString(4, body)
                ps.executeUpdate()
            }
            Db.conn.prepareStatement("SELECT last_insert_rowid()").use { ps ->
                ps.executeQuery().use { rs -> if (rs.next()) id = rs.getLong(1) }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("id" to id))
    }

    fun thread(ctx: io.javalin.http.Context, uid: Long) {
        val peer = ctx.queryParam("user") ?: ""
        val after = ctx.queryParam("after_id")?.toLongOrNull() ?: 0
        val limit = (ctx.queryParam("limit")?.toIntOrNull() ?: 50).coerceIn(1, 100)
        val pid = peer.toLongOrNull() ?: userIdByName(peer.trim())
        if (pid == null || pid <= 0) {
            HttpUtil.writeErr(ctx, 404, "No such user")
            return
        }
        if (!isFriend(uid, pid)) {
            HttpUtil.writeErr(ctx, 403, "Not friends")
            return
        }
        val out = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            val sql = if (after > 0) {
                "SELECT id,sender_id,recipient_id,nonce,body,created_at FROM dm_messages " +
                    "WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) " +
                    "AND id>? ORDER BY id ASC LIMIT ?"
            } else {
                "SELECT id,sender_id,recipient_id,nonce,body,created_at FROM dm_messages " +
                    "WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) " +
                    "ORDER BY id DESC LIMIT ?"
            }
            Db.conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, uid); ps.setLong(2, pid); ps.setLong(3, pid); ps.setLong(4, uid)
                if (after > 0) {
                    ps.setLong(5, after); ps.setInt(6, limit)
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
                                "nonce" to rs.getString(4),
                                "body" to rs.getString(5),
                                "created_at" to rs.getString(6)
                            )
                        )
                    }
                }
            }
        }
        if (after <= 0) out.reverse()
        HttpUtil.writeJson(ctx, 200, mapOf("messages" to out))
    }

    fun recent(ctx: io.javalin.http.Context, uid: Long) {
        val limit = (ctx.queryParam("limit")?.toIntOrNull() ?: 20).coerceIn(1, 50)
        val out = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "SELECT m.id,m.sender_id,m.recipient_id,m.nonce,m.body,m.created_at,u.username " +
                    "FROM dm_messages m JOIN users u ON u.id=m.sender_id " +
                    "WHERE m.sender_id=? OR m.recipient_id=? ORDER BY m.id DESC LIMIT ?"
            ).use { ps ->
                ps.setLong(1, uid); ps.setLong(2, uid); ps.setInt(3, limit)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        out.add(
                            mapOf(
                                "id" to rs.getLong(1),
                                "sender_id" to rs.getLong(2),
                                "recipient_id" to rs.getLong(3),
                                "nonce" to rs.getString(4),
                                "body" to rs.getString(5),
                                "created_at" to rs.getString(6),
                                "username" to (rs.getString(7) ?: "")
                            )
                        )
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("messages" to out))
    }
}
