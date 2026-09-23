package watchshark

import io.javalin.http.Context
import java.io.File

object AdminHandlers {
    fun requireAdmin(ctx: Context): Triple<Long, String, Boolean> {
        val (uid, un, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return Triple(0, "", false)
        }
        if (!Auth.isAdmin(uid, un)) {
            HttpUtil.writeErr(ctx, 403, "Forbidden")
            return Triple(0, "", false)
        }
        return Triple(uid, un, true)
    }

    fun pending(ctx: Context) {
        val items = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT id,username,created_at FROM users WHERE verified=0 ORDER BY id ASC").use { rs ->
                    while (rs.next()) items.add(
                        mapOf("id" to rs.getLong(1), "username" to (rs.getString(2) ?: ""), "created_at" to (rs.getString(3) ?: ""))
                    )
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("pending" to items))
    }

    fun approve(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        val id = node?.get("id")?.asLong() ?: 0
        if (node == null || id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        var changed = 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET verified=1 WHERE id=? AND verified=0").use { ps ->
                ps.setLong(1, id)
                changed = ps.executeUpdate()
            }
        }
        if (changed == 0) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun adminDeluserRow(id: Long): Boolean {
        val fns = mutableListOf<String>()
        val ths = mutableListOf<String>()
        var uav: String? = null
        var changed = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT filename,thumbnail FROM videos WHERE user_id=? LIMIT 64").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        fns.add(rs.getString(1) ?: "")
                        ths.add(rs.getString(2) ?: "")
                    }
                }
            }
            Db.conn.prepareStatement("SELECT avatar FROM users WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) uav = rs.getString(1) }
            }
            Db.conn.prepareStatement("DELETE FROM likes WHERE user_id=?").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
            Db.conn.prepareStatement("DELETE FROM likes WHERE video_id IN (SELECT id FROM videos WHERE user_id=?)").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
            Db.conn.prepareStatement("DELETE FROM users WHERE id=?").use { ps ->
                ps.setLong(1, id)
                changed = ps.executeUpdate() > 0
            }
        }
        if (!changed) return false
        for (i in fns.indices) {
            File("${Config.videosDir}/${fns[i]}").delete()
            Media.unlinkRenditions(fns[i])
            if (ths[i].isNotEmpty()) File("${Config.thumbsDir}/${ths[i]}").delete()
        }
        if (!uav.isNullOrEmpty()) File("${Config.avatarsDir}/$uav").delete()
        return true
    }

    fun reject(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        val id = node?.get("id")?.asLong() ?: 0
        if (node == null || id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        if (!adminDeluserRow(id)) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun users(ctx: Context) {
        val items = mutableListOf<Map<String, Any?>>()
        val now = System.currentTimeMillis() / 1000
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT id,username,verified,role,created_at,banned_until,ban_reason,deleted,deleted_reason FROM users ORDER BY id ASC").use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        val username = rs.getString(2) ?: ""
                        val v = rs.getInt(3) != 0
                        val role = rs.getString(4) ?: "user"
                        val ca = rs.getString(5) ?: ""
                        val untilObj = rs.getObject(6)
                        val until = (untilObj as? Number)?.toLong() ?: 0
                        val breason = rs.getString(7) ?: ""
                        val del = rs.getInt(8) != 0
                        val dreason = rs.getString(9) ?: ""
                        var banned = false
                        var daysLeft = 0.0
                        if (until > now && !del) {
                            banned = true
                            daysLeft = if (until > 4102444800L) -1.0 else (until - now).toDouble() / 86400.0
                        }
                        items.add(
                            mapOf(
                                "id" to id, "username" to username, "verified" to v,
                                "role" to role.ifEmpty { "user" }, "created_at" to ca,
                                "banned" to banned, "ban_days_left" to daysLeft,
                                "ban_reason" to breason, "banned_until" to until,
                                "deleted" to del, "deleted_reason" to dreason
                            )
                        )
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("users" to items))
    }

    fun ban(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val id = node.get("id")?.asLong() ?: 0
        if (id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val reason = HttpUtil.truncateRunes(node.get("reason")?.asText() ?: "", 500)
        val perm = node.get("permanent")?.asBoolean() == true
        val until: Long
        if (perm) {
            until = 9999999999L
        } else {
            val days = node.get("days")?.asDouble() ?: 0.0
            val hours = node.get("hours")?.asDouble() ?: 0.0
            val minutes = node.get("minutes")?.asDouble() ?: 0.0
            val totalSec = days * 86400 + hours * 3600 + minutes * 60
            if (totalSec <= 0) {
                HttpUtil.writeErr(ctx, 400, "Give a ban duration")
                return
            }
            until = System.currentTimeMillis() / 1000 + totalSec.toLong()
        }
        var changed = 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET banned_until=?, ban_reason=? WHERE id=?").use { ps ->
                ps.setLong(1, until); ps.setString(2, reason); ps.setLong(3, id)
                changed = ps.executeUpdate()
            }
        }
        if (changed == 0) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "banned_until" to until))
    }

    fun unban(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        val id = node?.get("id")?.asLong() ?: 0
        if (node == null || id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET banned_until=0, ban_reason=NULL WHERE id=?").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun softDelete(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        val id = node?.get("id")?.asLong() ?: 0
        if (node == null || id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val reason = HttpUtil.truncateRunes(node.get("reason")?.asText() ?: "", 500)
        val fns = mutableListOf<String>()
        val ths = mutableListOf<String>()
        var changed = 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT filename,thumbnail FROM videos WHERE user_id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        fns.add(rs.getString(1) ?: "")
                        ths.add(rs.getString(2) ?: "")
                    }
                }
            }
            Db.conn.prepareStatement("DELETE FROM likes WHERE video_id IN (SELECT id FROM videos WHERE user_id=?)").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
            Db.conn.prepareStatement("DELETE FROM videos WHERE user_id=?").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
            Db.conn.prepareStatement("UPDATE users SET deleted=1, deleted_reason=?, banned_until=0 WHERE id=?").use { ps ->
                ps.setString(1, reason); ps.setLong(2, id)
                changed = ps.executeUpdate()
            }
        }
        if (changed == 0) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        for (i in fns.indices) {
            File("${Config.videosDir}/${fns[i]}").delete()
            Media.unlinkRenditions(fns[i])
            if (ths[i].isNotEmpty()) File("${Config.thumbsDir}/${ths[i]}").delete()
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun restore(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        val id = node?.get("id")?.asLong() ?: 0
        if (node == null || id <= 0) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET deleted=0, deleted_reason=NULL, banned_until=0, ban_reason=NULL WHERE id=?").use { ps ->
                ps.setLong(1, id); ps.executeUpdate()
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun delUser(ctx: Context, id: Long, self: Long) {
        if (id == self) {
            HttpUtil.writeErr(ctx, 400, "Cannot remove yourself")
            return
        }
        if (!adminDeluserRow(id)) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }
}
