package watchshark

import io.javalin.http.Context
import java.io.File

object AuthHandlers {
    fun signup(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val u = (node.get("username")?.asText() ?: "").trim()
        val e = (node.get("email")?.asText() ?: "").trim().lowercase()
        val p = node.get("password")?.asText() ?: ""
        if (!Auth.validUsername(u)) {
            HttpUtil.writeErr(ctx, 400, "Username: 3-30 chars, letters/numbers/_")
            return
        }
        if (!Auth.validEmail(e)) {
            HttpUtil.writeErr(ctx, 400, "Invalid email")
            return
        }
        if (p.length < 6 || p.length > 200) {
            HttpUtil.writeErr(ctx, 400, "Password must be 6+ chars")
            return
        }
        val hh = Auth.pwHash(p)
        if (hh == null) {
            HttpUtil.writeErr(ctx, 500, "Hashing failed")
            return
        }
        var id = 0L
        var conflict = false
        var cap = false
        synchronized(Db.lock) {
            var nusers = 0L
            var nmail = 0L
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM users").use { rs -> if (rs.next()) nusers = rs.getLong(1) }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM users WHERE lower(email)=?").use { ps ->
                ps.setString(1, e)
                ps.executeQuery().use { rs -> if (rs.next()) nmail = rs.getLong(1) }
            }
            var role = "user"
            if (nusers == 0L) role = "admin"
            if (nmail >= 5) {
                cap = true
            } else {
                try {
                    Db.conn.prepareStatement("INSERT INTO users (username,email,password_hash,verified,verify_token,role) VALUES (?,?,?,?,?,?)").use { ps ->
                        ps.setString(1, u)
                        ps.setString(2, e)
                        ps.setString(3, hh)
                        ps.setInt(4, 1)
                        ps.setNull(5, java.sql.Types.VARCHAR)
                        ps.setString(6, role)
                        ps.executeUpdate()
                    }
                    id = Db.lastInsertId()
                } catch (ex: Exception) {
                    if (ex.message.orEmpty().contains("UNIQUE constraint")) conflict = true
                }
            }
        }
        if (cap) {
            HttpUtil.writeErr(ctx, 400, "Max 5 accounts per email")
            return
        }
        if (conflict || id == 0L) {
            if (conflict) HttpUtil.writeErr(ctx, 409, "Username or email already taken")
            else HttpUtil.writeErr(ctx, 500, "Signup failed")
            return
        }
        Media.sendMail(e, "Welcome to WatchShark", "Welcome to WatchShark, $u!\n\nYour account is ready — just log in and start watching.\n")
        HandlersCommon.setAuthCookie(ctx, id, u)
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "user" to mapOf("id" to id, "username" to u)))
    }

    fun login(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val l = (node.get("login")?.asText() ?: "").trim().lowercase()
        val pw = node.get("password")?.asText() ?: ""
        var id = 0L
        var un = ""
        var hh = ""
        var verified = 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id,username,password_hash,verified FROM users WHERE lower(username)=? OR lower(email)=?").use { ps ->
                ps.setString(1, l)
                ps.setString(2, l)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        id = rs.getLong(1)
                        un = rs.getString(2) ?: ""
                        hh = rs.getString(3) ?: ""
                        verified = rs.getInt(4)
                    } else {
                        id = 0
                    }
                }
            }
        }
        if (id == 0L || !Auth.pwVerify(pw, hh)) {
            HttpUtil.writeErr(ctx, 401, "Wrong login or password")
            return
        }
        if (verified == 0) {
            HttpUtil.writeErr(ctx, 403, "Waiting for admin approval")
            return
        }
        HandlersCommon.setAuthCookie(ctx, id, un)
        val st = Auth.getBanState(id)
        HttpUtil.writeJson(
            ctx, 200, mapOf(
                "ok" to true,
                "user" to mapOf("id" to id, "username" to un),
                "banned" to st.banned, "ban_days_left" to st.daysLeft, "ban_reason" to st.reason,
                "deleted" to st.deleted, "deleted_reason" to st.delReason
            )
        )
    }

    fun logout(ctx: Context) {
        HandlersCommon.clearAuthCookie(ctx)
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun verify(ctx: Context) {
        val tok = ctx.queryParam("token") ?: ""
        if (tok.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "Missing token")
            return
        }
        var changed = 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET verified=1, verify_token=NULL WHERE verify_token=?").use { ps ->
                ps.setString(1, tok)
                changed = ps.executeUpdate()
            }
        }
        if (changed == 0) {
            HttpUtil.writeErr(ctx, 400, "Invalid or expired link")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun resend(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val e = (node.get("email")?.asText() ?: "").trim().lowercase()
        var id = 0L
        var vtok = ""
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id FROM users WHERE lower(email)=? AND verified=0").use { ps ->
                ps.setString(1, e)
                ps.executeQuery().use { rs -> if (rs.next()) id = rs.getLong(1) }
            }
            if (id != 0L) {
                vtok = Auth.randHex(32)
                Db.conn.prepareStatement("UPDATE users SET verify_token=? WHERE id=?").use { ps ->
                    ps.setString(1, vtok)
                    ps.setLong(2, id)
                    ps.executeUpdate()
                }
            }
        }
        if (id != 0L && e.isNotEmpty()) {
            val link = "${Config.siteBase()}/verify?token=$vtok"
            Media.sendMail(e, "Confirm your WatchShark account", "Welcome to WatchShark!\n\nConfirm your account by opening this link:\n$link\n")
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun forgot(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val e = (node.get("email")?.asText() ?: "").trim().lowercase()
        data class Acc(val id: Long, val name: String, var tok: String = "", var made: Boolean = false)
        val accs = mutableListOf<Acc>()
        if (e.isNotEmpty()) {
            synchronized(Db.lock) {
                Db.conn.prepareStatement("SELECT id,username FROM users WHERE lower(email)=? LIMIT 5").use { ps ->
                    ps.setString(1, e)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) accs.add(Acc(rs.getLong(1), rs.getString(2) ?: ""))
                    }
                }
                val exp = System.currentTimeMillis() / 1000 + 3600
                for (a in accs) {
                    val tok = Auth.randHex(32)
                    Db.conn.prepareStatement("DELETE FROM resets WHERE user_id=?").use { ps ->
                        ps.setLong(1, a.id); ps.executeUpdate()
                    }
                    try {
                        Db.conn.prepareStatement("INSERT INTO resets (token,user_id,expires,used) VALUES (?,?,?,0)").use { ps ->
                            ps.setString(1, tok); ps.setLong(2, a.id); ps.setLong(3, exp)
                            ps.executeUpdate()
                        }
                        a.tok = tok
                        a.made = true
                    } catch (_: Exception) {}
                }
            }
            val sb = StringBuilder("Someone asked to reset WatchShark password(s).\n")
            var sent = false
            for (a in accs) {
                if (!a.made) continue
                sent = true
                sb.append("\nAccount '${a.name}' (valid 1 hour):\n${Config.siteBase()}/reset?token=${a.tok}\n")
            }
            if (sent) {
                sb.append("\nIgnore this if it was not you.\n")
                Media.sendMail(e, "Reset your WatchShark password", sb.toString())
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun reset(ctx: Context) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val token = node.get("token")?.asText() ?: ""
        val password = node.get("password")?.asText() ?: ""
        if (token.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "Missing token")
            return
        }
        if (password.length < 6 || password.length > 200) {
            HttpUtil.writeErr(ctx, 400, "Password must be 6+ chars")
            return
        }
        val now = System.currentTimeMillis() / 1000
        var ok = false
        synchronized(Db.lock) {
            var uid = 0L
            var exp = 0L
            var used = 0
            var found = false
            Db.conn.prepareStatement("SELECT user_id,expires,used FROM resets WHERE token=?").use { ps ->
                ps.setString(1, token)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        uid = rs.getLong(1); exp = rs.getLong(2); used = rs.getInt(3); found = true
                    }
                }
            }
            if (found && used == 0 && exp > now && uid != 0L) {
                val hh = Auth.pwHash(password)
                if (hh != null) {
                    Db.conn.prepareStatement("UPDATE users SET password_hash=?, verified=1 WHERE id=?").use { ps ->
                        ps.setString(1, hh); ps.setLong(2, uid)
                        if (ps.executeUpdate() > 0) {
                            Db.conn.prepareStatement("UPDATE resets SET used=1 WHERE token=?").use { ps2 ->
                                ps2.setString(1, token)
                                if (ps2.executeUpdate() >= 0) ok = true
                            }
                        }
                    }
                }
            }
        }
        if (!ok) {
            HttpUtil.writeErr(ctx, 400, "Invalid or expired link")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun change(ctx: Context, uid: Long) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val current = node.get("current")?.asText() ?: ""
        val password = node.get("password")?.asText() ?: ""
        if (password.length < 6 || password.length > 200) {
            HttpUtil.writeErr(ctx, 400, "Password must be 6+ chars")
            return
        }
        var ok = false
        synchronized(Db.lock) {
            var hh = ""
            var found = false
            Db.conn.prepareStatement("SELECT password_hash FROM users WHERE id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) { hh = rs.getString(1) ?: ""; found = true } }
            }
            if (found && Auth.pwVerify(current, hh)) {
                val nh = Auth.pwHash(password)
                if (nh != null) {
                    Db.conn.prepareStatement("UPDATE users SET password_hash=? WHERE id=?").use { ps ->
                        ps.setString(1, nh); ps.setLong(2, uid)
                        if (ps.executeUpdate() > 0) ok = true
                    }
                }
            }
        }
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Wrong current password")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun username(ctx: Context, uid: Long) {
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val u = (node.get("username")?.asText() ?: "").trim()
        if (!Auth.validUsername(u)) {
            HttpUtil.writeErr(ctx, 400, "Username: 3-30 chars, letters/numbers/_")
            return
        }
        var conflict = false
        var ok = false
        synchronized(Db.lock) {
            try {
                Db.conn.prepareStatement("UPDATE users SET username=? WHERE id=?").use { ps ->
                    ps.setString(1, u); ps.setLong(2, uid)
                    if (ps.executeUpdate() > 0) ok = true
                }
            } catch (ex: Exception) {
                if (ex.message.orEmpty().contains("UNIQUE constraint")) conflict = true
            }
        }
        if (conflict) {
            HttpUtil.writeErr(ctx, 409, "Username already taken")
            return
        }
        if (!ok) {
            HttpUtil.writeErr(ctx, 500, "Change failed")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun notifSet(ctx: Context, uid: Long) {
        val node = HandlersCommon.readJson(ctx) ?: return
        val uploadsNode = node.get("uploads") ?: return
        if (!uploadsNode.isBoolean) return
        val on = if (uploadsNode.asBoolean()) 1 else 0
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE users SET notify_uploads=? WHERE id=?").use { ps ->
                ps.setInt(1, on); ps.setLong(2, uid); ps.executeUpdate()
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun me(ctx: Context) {
        val (id, un, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeJson(ctx, 200, mapOf("user" to null))
            return
        }
        var avatar: String? = null
        var notif = 0
        var since = ""
        var iat = 0L
        var exp = 0L
        ctx.cookie("ws_token")?.let { tok ->
            val parts = tok.split(".")
            if (parts.size == 3) {
                try {
                    val payload = Auth.b64urlDecStr(parts[1])
                    val c = Auth.json.readTree(payload)
                    iat = c.get("iat")?.asLong() ?: 0
                    exp = c.get("exp")?.asLong() ?: 0
                } catch (_: Exception) {}
            }
        }
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT avatar,notify_uploads,created_at FROM users WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        avatar = rs.getString(1)
                        notif = rs.getInt(2)
                        since = rs.getString(3) ?: ""
                    }
                }
            }
        }
        val av: Any? = if (!avatar.isNullOrEmpty()) "/a/$avatar" else null
        val st = Auth.getBanState(id)
        HttpUtil.writeJson(
            ctx, 200, mapOf(
                "user" to mapOf(
                    "id" to id, "username" to un, "iat" to iat, "exp" to exp,
                    "admin" to Auth.isAdmin(id, un), "avatar" to av,
                    "notify_uploads" to (notif != 0), "since" to since,
                    "banned" to st.banned, "ban_days_left" to st.daysLeft, "ban_reason" to st.reason,
                    "deleted" to st.deleted, "deleted_reason" to st.delReason,
                    // The requester just hit the API: online by definition.
                    "online" to true
                )
            )
        )
    }
}
