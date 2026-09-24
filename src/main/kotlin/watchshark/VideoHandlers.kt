package watchshark

import io.javalin.http.Context
import java.io.File

object VideoHandlers {
    fun list(ctx: Context) {
        var viewer = -1L
        val (aid, _, aok) = HandlersCommon.authUser(ctx)
        if (aok) viewer = aid
        var search = ctx.queryParam("q") ?: ""
        if (search.codePoints().count() > 100) {
            search = String(search.codePoints().toArray().copyOf(100), 0, 100)
        }
        search = search.trim()
        val popular = ctx.queryParam("sort") == "popular"
        val order = if (popular) "v.views DESC, v.id DESC" else "v.id DESC"
        var page = ctx.queryParam("page")?.toLongOrNull() ?: 1
        if (page < 1) page = 1
        var limit = ctx.queryParam("limit")?.toLongOrNull() ?: 12
        if (limit < 1) limit = 12
        if (limit > 24) limit = 24
        val off = (page - 1) * limit
        val mine = ctx.queryParam("mine") == "1"
        var kind = ctx.queryParam("kind") ?: ""
        if (kind != "music") kind = "video"
        if (mine && viewer < 0) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        val ids = mutableListOf<Long>()
        var total = 0L
        synchronized(Db.lock) {
            if (mine) {
                val like = "%${HttpUtil.escapeLike(search)}%"
                Db.conn.prepareStatement(
                    "SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE v.user_id=? AND COALESCE(v.kind,'video')!='music' AND (?='' OR v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\') ORDER BY $order LIMIT ? OFFSET ?"
                ).use { ps ->
                    ps.setLong(1, viewer)
                    ps.setString(2, search)
                    ps.setString(3, like); ps.setString(4, like); ps.setString(5, like)
                    ps.setLong(6, limit); ps.setLong(7, off)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
                }
                Db.conn.prepareStatement(
                    "SELECT COUNT(*) FROM videos v JOIN users u ON u.id=v.user_id WHERE v.user_id=? AND COALESCE(v.kind,'video')!='music' AND (?='' OR v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')"
                ).use { ps ->
                    ps.setLong(1, viewer)
                    ps.setString(2, search)
                    ps.setString(3, like); ps.setString(4, like); ps.setString(5, like)
                    ps.executeQuery().use { rs -> if (rs.next()) total = rs.getLong(1) }
                }
            } else if (search.isEmpty()) {
                Db.conn.prepareStatement("SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? ORDER BY $order LIMIT ? OFFSET ?").use { ps ->
                    ps.setString(1, kind)
                    ps.setLong(2, limit); ps.setLong(3, off)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
                }
                Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE COALESCE(kind,'video')=?").use { ps ->
                    ps.setString(1, kind)
                    ps.executeQuery().use { rs -> if (rs.next()) total = rs.getLong(1) }
                }
            } else {
                val like = "%${HttpUtil.escapeLike(search)}%"
                Db.conn.prepareStatement(
                    "SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\') ORDER BY $order LIMIT ? OFFSET ?"
                ).use { ps ->
                    ps.setString(1, kind)
                    ps.setString(2, like); ps.setString(3, like); ps.setString(4, like)
                    ps.setLong(5, limit); ps.setLong(6, off)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
                }
                Db.conn.prepareStatement(
                    "SELECT COUNT(*) FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')=? AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')"
                ).use { ps ->
                    ps.setString(1, kind)
                    ps.setString(2, like); ps.setString(3, like); ps.setString(4, like)
                    ps.executeQuery().use { rs -> if (rs.next()) total = rs.getLong(1) }
                }
            }
        }
        val videos = mutableListOf<Any?>()
        for (id in ids) {
            videos.add(HandlersCommon.videoJson(id, viewer))
        }
        var pages = if (limit < 1) 0 else (total + limit - 1) / limit
        HttpUtil.writeJson(ctx, 200, mapOf("videos" to videos, "page" to page, "pages" to pages, "total" to total))
    }

    fun getVideo(ctx: Context, id: Long) {
        var viewer = -1L
        val (aid, _, aok) = HandlersCommon.authUser(ctx)
        if (aok) viewer = aid
        var exists = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT 1 FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> exists = rs.next() }
            }
            if (exists && viewer >= 0) {
                HandlersCommon.recordViewLocked(id, viewer, "")
            }
        }
        if (!exists) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val v = HandlersCommon.videoJson(id, viewer)
        if (v == null) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val comments = mutableListOf<Map<String, Any?>>()
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT c.id,c.body,c.created_at,u.username,u.avatar,c.parent_id,pu.username FROM comments c JOIN users u ON u.id=c.user_id LEFT JOIN comments pc ON pc.id=c.parent_id LEFT JOIN users pu ON pu.id=pc.user_id WHERE c.video_id=? ORDER BY c.id DESC LIMIT 50").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val av = rs.getString(5)
                        val pid = rs.getObject(6)?.toString()?.toLongOrNull()
                        val pun = rs.getString(7)
                        comments.add(
                            mapOf(
                                "id" to rs.getLong(1), "body" to (rs.getString(2) ?: ""),
                                "created_at" to (rs.getString(3) ?: ""),
                                "username" to (rs.getString(4) ?: ""),
                                "avatar" to if (!av.isNullOrEmpty()) "/a/$av" else null,
                                "parent_id" to pid,
                                "parent_username" to pun
                            )
                        )
                    }
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("video" to v, "comments" to comments))
    }

    fun deleteVideo(ctx: Context, id: Long) {
        val (uid, un, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        var reason = ""
        try {
            val body = ctx.body()
            if (body.isNotEmpty()) {
                val n = Auth.json.readTree(body)
                reason = HttpUtil.truncateRunes(n.get("reason")?.asText() ?: "", 500)
            }
        } catch (_: Exception) {}
        val admin = Auth.isAdmin(uid, un)
        var owner = 0L
        var fn = ""
        var title = ""
        var th: String? = null
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT user_id,filename,thumbnail,title FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        owner = rs.getLong(1); fn = rs.getString(2) ?: ""; th = rs.getString(3); title = rs.getString(4) ?: ""
                        found = true
                    }
                }
            }
            if (found) {
                if (owner != uid && !admin) {
                    HttpUtil.writeErr(ctx, 403, "Not yours")
                    return
                }
                if (owner != uid && reason.isEmpty()) {
                    HttpUtil.writeErr(ctx, 400, "Reason required")
                    return
                }
                if (owner != uid) {
                    Db.conn.prepareStatement("INSERT INTO notifications (user_id,video_id,kind,title,text) VALUES (?,NULL,'delete',?,?)").use { ps ->
                        ps.setLong(1, owner); ps.setString(2, title); ps.setString(3, reason)
                        ps.executeUpdate()
                    }
                }
                Db.conn.prepareStatement("DELETE FROM likes WHERE video_id=?").use { ps ->
                    ps.setLong(1, id); ps.executeUpdate()
                }
                Db.conn.prepareStatement("DELETE FROM videos WHERE id=?").use { ps ->
                    ps.setLong(1, id); ps.executeUpdate()
                }
            }
        }
        if (!found) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        Config.resolveVideo(fn).delete()
        Media.unlinkRenditions(fn)
        if (!th.isNullOrEmpty()) File("${Config.thumbsDir}/$th").delete()
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun thumb(ctx: Context, id: Long) {
        val (uid, un, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        val admin = Auth.isAdmin(uid, un)
        var owner = 0L
        var oldTh: String? = null
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT user_id,thumbnail FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        owner = rs.getLong(1); oldTh = rs.getString(2); found = true
                    }
                }
            }
        }
        if (!found) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        if (owner != uid && !admin) {
            HttpUtil.writeErr(ctx, 403, "Not yours")
            return
        }
        val ct = ctx.header("Content-Type") ?: ""
        if (ct.isEmpty() || !ct.startsWith("image/")) {
            HttpUtil.writeErr(ctx, 400, "Send an image file")
            return
        }
        val body = readLimited(ctx, 10L * 1024 * 1024 + 1) ?: run {
            HttpUtil.writeErr(ctx, 400, "Empty or too big (max 10MB)")
            return
        }
        if (body.isEmpty() || body.size > 10 * 1024 * 1024) {
            HttpUtil.writeErr(ctx, 400, "Empty or too big (max 10MB)")
            return
        }
        val stem = Auth.randHex(16)
        val tmp = File("${Config.thumbsDir}/$stem.up")
        try {
            tmp.writeBytes(body)
        } catch (_: Exception) {
            HttpUtil.writeErr(ctx, 500, "Cannot store file")
            return
        }
        val (name, good) = Media.convertThumb(tmp.absolutePath, stem)
        tmp.delete()
        if (!good) {
            HttpUtil.writeErr(ctx, 400, "Not an image file")
            return
        }
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET thumbnail=? WHERE id=?").use { ps ->
                ps.setString(1, name); ps.setLong(2, id); ps.executeUpdate()
            }
        }
        if (!oldTh.isNullOrEmpty() && oldTh != name) File("${Config.thumbsDir}/$oldTh").delete()
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "thumbnail" to "/t/$name"))
    }

    fun editVideo(ctx: Context, id: Long) {
        val (uid, un, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        val admin = Auth.isAdmin(uid, un)
        var owner = 0L
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT user_id FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) { owner = rs.getLong(1); found = true } }
            }
        }
        if (!found) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        if (owner != uid && !admin) {
            HttpUtil.writeErr(ctx, 403, "Not yours")
            return
        }
        val title = HttpUtil.truncateRunes(ctx.formParam("title") ?: "", 120)
        val desc = HttpUtil.truncateRunes(ctx.formParam("description") ?: "", 2000)
        if (title.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "Title required")
            return
        }
        var thumbName = ""
        var hasThumb = false
        try {
            val files = ctx.uploadedFiles("thumb")
            if (files.isNotEmpty()) {
                val f = files[0]
                val body = f.content().readBytes()
                if (body.isEmpty() || body.size > 10 * 1024 * 1024) {
                    HttpUtil.writeErr(ctx, 400, "Thumbnail too big (max 10MB)")
                    return
                }
                val stem = Auth.randHex(16)
                val tmp = File("${Config.thumbsDir}/$stem.up")
                tmp.writeBytes(body)
                val (name, good) = Media.convertThumb(tmp.absolutePath, stem)
                tmp.delete()
                if (!good) {
                    HttpUtil.writeErr(ctx, 400, "Not an image file")
                    return
                }
                thumbName = name
                hasThumb = true
            }
        } catch (_: Exception) {}
        synchronized(Db.lock) {
            if (hasThumb) {
                var oldTh: String? = null
                Db.conn.prepareStatement("SELECT thumbnail FROM videos WHERE id=?").use { ps ->
                    ps.setLong(1, id)
                    ps.executeQuery().use { rs -> if (rs.next()) oldTh = rs.getString(1) }
                }
                Db.conn.prepareStatement("UPDATE videos SET title=?,description=?,thumbnail=? WHERE id=?").use { ps ->
                    ps.setString(1, title); ps.setString(2, desc); ps.setString(3, thumbName); ps.setLong(4, id)
                    ps.executeUpdate()
                }
                if (!oldTh.isNullOrEmpty() && oldTh != thumbName) File("${Config.thumbsDir}/$oldTh").delete()
            } else {
                Db.conn.prepareStatement("UPDATE videos SET title=?,description=? WHERE id=?").use { ps ->
                    ps.setString(1, title); ps.setString(2, desc); ps.setLong(3, id)
                    ps.executeUpdate()
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun like(ctx: Context, id: Long) {
        val (uid, _, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        var exists = false
        var has = false
        var likes = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT 1 FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> exists = rs.next() }
            }
            if (exists) {
                Db.conn.prepareStatement("SELECT 1 FROM likes WHERE user_id=? AND video_id=?").use { ps ->
                    ps.setLong(1, uid); ps.setLong(2, id)
                    ps.executeQuery().use { rs -> has = rs.next() }
                }
                if (has) {
                    Db.conn.prepareStatement("DELETE FROM likes WHERE user_id=? AND video_id=?").use { ps ->
                        ps.setLong(1, uid); ps.setLong(2, id); ps.executeUpdate()
                    }
                } else {
                    Db.conn.prepareStatement("INSERT INTO likes (user_id,video_id) VALUES (?,?)").use { ps ->
                        ps.setLong(1, uid); ps.setLong(2, id); ps.executeUpdate()
                    }
                }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM likes WHERE video_id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) likes = rs.getLong(1) }
            }
        }
        if (!exists) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "liked" to !has, "likes" to likes))
    }

    fun comment(ctx: Context, id: Long) {
        val (uid, _, ok) = HandlersCommon.authUser(ctx)
        if (!ok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return
        }
        val node = HandlersCommon.readJson(ctx)
        if (node == null) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        val body = HttpUtil.truncateRunes(node.get("body")?.asText() ?: "", 2000)
        if (body.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "Empty comment")
            return
        }
        val parentId = node.get("parent_id")?.takeUnless { it.isNull }?.asLong()
        var exists = false
        var cid = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT 1 FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> exists = rs.next() }
            }
            if (parentId != null) {
                var pv = -1L
                Db.conn.prepareStatement("SELECT video_id FROM comments WHERE id=?").use { ps ->
                    ps.setLong(1, parentId)
                    ps.executeQuery().use { rs -> if (rs.next()) pv = rs.getLong(1) }
                }
                if (pv != id) {
                    HttpUtil.writeErr(ctx, 400, "Bad parent comment")
                    return
                }
            }
            if (exists) {
                if (parentId != null) {
                    Db.conn.prepareStatement("INSERT INTO comments (video_id,user_id,body,parent_id) VALUES (?,?,?,?)").use { ps ->
                        ps.setLong(1, id); ps.setLong(2, uid); ps.setString(3, body); ps.setLong(4, parentId)
                        ps.executeUpdate()
                    }
                } else {
                    Db.conn.prepareStatement("INSERT INTO comments (video_id,user_id,body) VALUES (?,?,?)").use { ps ->
                        ps.setLong(1, id); ps.setLong(2, uid); ps.setString(3, body)
                        ps.executeUpdate()
                    }
                }
                cid = Db.lastInsertId()
            }
        }
        if (!exists) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "id" to cid))
    }

    fun wheels(ctx: Context) {
        var viewer = -1L
        val (aid, _, aok) = HandlersCommon.authUser(ctx)
        if (aok) viewer = aid
        val seen = mutableListOf<Long>()
        val s = ctx.queryParam("seen") ?: ""
        if (s.isNotEmpty()) {
            for (p in s.split(",")) {
                if (seen.size >= 128) break
                val v = p.trim().toLongOrNull()
                if (v != null && v > 0) seen.add(v)
            }
        }
        val sb = StringBuilder("SELECT id FROM videos WHERE status='ready' AND COALESCE(kind,'video')='wheel'")
        if (seen.isNotEmpty()) {
            sb.append(" AND id NOT IN (")
            sb.append(seen.joinToString(",") { "?" })
            sb.append(")")
        }
        sb.append(" ORDER BY id DESC LIMIT 1")
        var vid = 0L
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement(sb.toString()).use { ps ->
                seen.forEachIndexed { i, v -> ps.setLong(i + 1, v) }
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        vid = rs.getLong(1); found = true
                    }
                }
            }
            if (found && viewer >= 0) HandlersCommon.recordViewLocked(vid, viewer, "")
        }
        if (!found) {
            HttpUtil.writeErr(ctx, 404, "No videos yet")
            return
        }
        val v = HandlersCommon.videoJson(vid, viewer)
        if (v == null) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("video" to v))
    }

    fun upload(ctx: Context, uid: Long) {
        val (blocked, msg) = Auth.isBlockedFromUpload(uid)
        if (blocked) {
            HttpUtil.writeErr(ctx, 403, msg)
            return
        }
        if (Media.storedBytes() >= Config.quotaBytes) {
            HttpUtil.writeErr(ctx, 507, "Server storage full (50GB limit reached)")
            return
        }
        val contentLength = ctx.req().contentLengthLong
        if (contentLength >= 0 && contentLength.toULong() + Media.storedBytes().toULong() > Config.quotaBytes.toULong()) {
            HttpUtil.writeErr(ctx, 507, "Server storage full (50GB limit reached)")
            return
        }
        var title = HttpUtil.truncateRunes(ctx.formParam("title") ?: "", 120)
        var desc = HttpUtil.truncateRunes(ctx.formParam("description") ?: "", 2000)
        var kind = HttpUtil.truncateRunes(ctx.formParam("kind") ?: "", 16)
        if (kind != "video" && kind != "wheel" && kind != "music") kind = "video"

        val fileParts = try { ctx.uploadedFiles("file") } catch (_: Exception) {
            HttpUtil.writeErr(ctx, 400, "Bad multipart")
            return
        }
        if (fileParts.isEmpty()) {
            HttpUtil.writeErr(ctx, 400, "No file")
            return
        }
        if (title.isEmpty()) title = "Untitled"

        val stem = Auth.randHex(16)
        val uname = Db.usernameOf(uid) ?: "u"
        val partPath = File(Config.userVideosDir(uname), "$stem.part")
        val thumbTmp = File(Config.userThumbsDir(uname), "$stem.ctmp")

        // save main file with limit
        try {
            partPath.outputStream().use { out ->
                val inp = fileParts[0].content()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > Config.maxBytes) {
                        out.close()
                        partPath.delete()
                        HttpUtil.writeErr(ctx, 413, "File too big (max ${Config.maxBytes / 1024 / 1024}MB)")
                        return
                    }
                    out.write(buf, 0, n)
                }
                if (total == 0L) {
                    out.close()
                    partPath.delete()
                    HttpUtil.writeErr(ctx, 400, "Empty file")
                    return
                }
            }
        } catch (_: Exception) {
            partPath.delete()
            HttpUtil.writeErr(ctx, 400, "Upload failed")
            return
        }
        val savedSize = partPath.length()

        // optional thumb
        var thumbSaved = false
        try {
            val thumbs = ctx.uploadedFiles("thumb")
            if (thumbs.isNotEmpty()) {
                val body = thumbs[0].content().readBytes()
                if (body.size > 10 * 1024 * 1024) {
                    partPath.delete()
                    HttpUtil.writeErr(ctx, 400, "Thumbnail too big (max 10MB)")
                    return
                }
                if (body.isNotEmpty()) {
                    thumbTmp.writeBytes(body)
                    thumbSaved = true
                }
            }
        } catch (_: Exception) {}

        if (Media.storedBytes() > Config.quotaBytes) {
            partPath.delete()
            if (thumbSaved) thumbTmp.delete()
            HttpUtil.writeErr(ctx, 507, "Server storage full (50GB limit reached)")
            return
        }
        if (kind == "music") {
            if (!Media.probeHasAudio(partPath.absolutePath)) {
                partPath.delete()
                if (thumbSaved) thumbTmp.delete()
                HttpUtil.writeErr(ctx, 400, "Not an audio file")
                return
            }
        } else if (!Media.probeHasVideo(partPath.absolutePath)) {
            partPath.delete()
            if (thumbSaved) thumbTmp.delete()
            HttpUtil.writeErr(ctx, 400, "Not a video file")
            return
        }

        var id = 0L
        synchronized(Db.lock) {
            try {
                Db.conn.prepareStatement("INSERT INTO videos (user_id,title,description,filename,thumbnail,mimetype,size,kind,status) VALUES (?,?,?,?,NULL,?,?,?,'processing')").use { ps ->
                    ps.setLong(1, uid)
                    ps.setString(2, title)
                    ps.setString(3, desc)
                    ps.setString(4, "$stem.part")
                    ps.setString(5, "video/mp4")
                    ps.setLong(6, savedSize)
                    ps.setString(7, kind)
                    ps.executeUpdate()
                }
                id = Db.lastInsertId()
            } catch (_: Exception) {}
        }
        if (id == 0L) {
            partPath.delete()
            if (thumbSaved) thumbTmp.delete()
            HttpUtil.writeErr(ctx, 500, "DB insert failed")
            return
        }
        val customThumb = if (thumbSaved) thumbTmp.absolutePath else ""
        val fid = id
        val fkind = kind
        if (fkind == "music") Media.bg.submit { Media.processMusic(fid, uid, partPath.absolutePath, stem, customThumb) }
        else Media.bg.submit { Media.processUpload(fid, uid, partPath.absolutePath, stem, customThumb) }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "id" to fid, "kind" to fkind))
    }

    fun follow(ctx: Context, uid: Long, target: Long) {
        if (target <= 0 || target == uid) {
            HttpUtil.writeErr(ctx, 400, "Bad request")
            return
        }
        var exists = false
        var has = false
        var followers = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT 1 FROM users WHERE id=?").use { ps ->
                ps.setLong(1, target)
                ps.executeQuery().use { rs -> exists = rs.next() }
            }
            if (exists) {
                Db.conn.prepareStatement("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?").use { ps ->
                    ps.setLong(1, uid); ps.setLong(2, target)
                    ps.executeQuery().use { rs -> has = rs.next() }
                }
                if (has) {
                    Db.conn.prepareStatement("DELETE FROM follows WHERE follower_id=? AND followed_id=?").use { ps ->
                        ps.setLong(1, uid); ps.setLong(2, target); ps.executeUpdate()
                    }
                } else {
                    Db.conn.prepareStatement("INSERT INTO follows (follower_id,followed_id) VALUES (?,?)").use { ps ->
                        ps.setLong(1, uid); ps.setLong(2, target); ps.executeUpdate()
                    }
                }
                Db.conn.prepareStatement("SELECT COUNT(*) FROM follows WHERE followed_id=?").use { ps ->
                    ps.setLong(1, target)
                    ps.executeQuery().use { rs -> if (rs.next()) followers = rs.getLong(1) }
                }
            }
        }
        if (!exists) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "following" to !has, "followers" to followers))
    }

    fun notifications(ctx: Context, uid: Long) {
        val items = mutableListOf<Map<String, Any?>>()
        var unread = 0L
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT n.id,n.video_id,n.created_at,n.read,n.kind,n.title,n.text,v.title,u.username FROM notifications n LEFT JOIN videos v ON v.id=n.video_id LEFT JOIN users u ON u.id=v.user_id WHERE n.user_id=? ORDER BY n.id DESC LIMIT 30").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val nid = rs.getLong(1)
                        val vidObj = rs.getObject(2)
                        val vid: Any? = if (rs.wasNull()) null else (vidObj as Number).toLong()
                        val ca = rs.getString(3) ?: ""
                        val rd = rs.getInt(4) != 0
                        val kind = rs.getString(5) ?: ""
                        val ntitle = rs.getString(6)
                        val ntext = rs.getString(7)
                        val vtitle = rs.getString(8)
                        val uname = rs.getString(9)
                        val title = if (!vtitle.isNullOrEmpty()) vtitle else (ntitle ?: "")
                        items.add(
                            mapOf(
                                "id" to nid, "video_id" to vid, "created_at" to ca, "read" to rd,
                                "title" to title, "username" to (uname ?: ""), "kind" to kind, "text" to (ntext ?: "")
                            )
                        )
                    }
                }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read=0").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) unread = rs.getLong(1) }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("notifications" to items, "unread" to unread))
    }

    fun notifRead(ctx: Context, uid: Long) {
        var nid: Long? = null
        try {
            val body = ctx.body()
            if (body.isNotEmpty()) {
                val n = Auth.json.readTree(body)
                val idNode = n.get("id")
                if (idNode != null && idNode.isNumber) nid = idNode.asLong()
            }
        } catch (_: Exception) {}
        synchronized(Db.lock) {
            if (nid != null && nid!! > 0) {
                Db.conn.prepareStatement("UPDATE notifications SET read=1 WHERE id=? AND user_id=?").use { ps ->
                    ps.setLong(1, nid!!); ps.setLong(2, uid); ps.executeUpdate()
                }
            } else {
                Db.conn.prepareStatement("UPDATE notifications SET read=1 WHERE user_id=?").use { ps ->
                    ps.setLong(1, uid); ps.executeUpdate()
                }
            }
        }
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true))
    }

    fun pfp(ctx: Context, uid: Long) {
        val ct = ctx.header("Content-Type") ?: ""
        if (ct.isEmpty() || !ct.startsWith("image/")) {
            HttpUtil.writeErr(ctx, 400, "Send an image file")
            return
        }
        if (Media.storedBytes() >= Config.quotaBytes) {
            HttpUtil.writeErr(ctx, 507, "Server storage full (50GB limit reached)")
            return
        }
        val body = readLimited(ctx, 50L * 1024 * 1024 + 1) ?: run {
            HttpUtil.writeErr(ctx, 400, "Empty or too big (max 50MB)")
            return
        }
        if (body.isEmpty() || body.size > 50 * 1024 * 1024) {
            HttpUtil.writeErr(ctx, 400, "Empty or too big (max 50MB)")
            return
        }
        val stem = Auth.randHex(16)
        val tmp = File("${Config.avatarsDir}/$stem.up")
        try {
            tmp.writeBytes(body)
        } catch (_: Exception) {
            HttpUtil.writeErr(ctx, 500, "Cannot store file")
            return
        }
        if (!Media.probeHasVideo(tmp.absolutePath)) {
            tmp.delete()
            HttpUtil.writeErr(ctx, 400, "Not an image file")
            return
        }
        val isGif = Media.probeFormat(tmp.absolutePath).contains("gif")
        var out = ""
        var name = ""
        var convertOk = false
        if (isGif) {
            out = "${Config.avatarsDir}/$stem.webm"
            name = "$stem.webm"
            val args = listOf("-y", "-i", tmp.absolutePath, "-vf", "scale=256:256:force_original_aspect_ratio=increase,crop=256:256", "-c:v", "libvpx-vp9", "-deadline", "good", "-cpu-used", "5", "-crf", "32", "-b:v", "0", "-an", "-f", "webm", out)
            if (!Media.runFFmpeg(args, java.time.Duration.ofSeconds(300)) || !Media.pfpSmallEnough(out)) {
                File(out).delete()
                val args2 = listOf("-y", "-i", tmp.absolutePath, "-vf", "scale=128:128:force_original_aspect_ratio=increase,crop=128:128", "-c:v", "libvpx-vp9", "-deadline", "good", "-cpu-used", "5", "-crf", "38", "-b:v", "0", "-an", "-f", "webm", out)
                if (!Media.runFFmpeg(args2, java.time.Duration.ofSeconds(300)) || !Media.pfpSmallEnough(out)) {
                    tmp.delete()
                    File(out).delete()
                    HttpUtil.writeErr(ctx, 500, "Conversion failed")
                    return
                }
            }
            convertOk = true
        } else {
            out = "${Config.avatarsDir}/$stem.webp"
            name = "$stem.webp"
            val args = listOf("-y", "-i", tmp.absolutePath, "-vf", "scale=256:256:force_original_aspect_ratio=increase,crop=256:256", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "80", out)
            if (!Media.runFFmpeg(args, java.time.Duration.ofSeconds(120)) || !Media.pfpSmallEnough(out)) {
                File(out).delete()
                val args2 = listOf("-y", "-i", tmp.absolutePath, "-vf", "scale=128:128:force_original_aspect_ratio=increase,crop=128:128", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "60", out)
                if (!Media.runFFmpeg(args2, java.time.Duration.ofSeconds(120)) || !Media.pfpSmallEnough(out)) {
                    tmp.delete()
                    File(out).delete()
                    HttpUtil.writeErr(ctx, 500, "Conversion failed")
                    return
                }
            }
            convertOk = true
        }
        tmp.delete()
        if (!convertOk) {
            HttpUtil.writeErr(ctx, 500, "Conversion failed")
            return
        }
        var old: String? = null
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT avatar FROM users WHERE id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) old = rs.getString(1) }
            }
            Db.conn.prepareStatement("UPDATE users SET avatar=? WHERE id=?").use { ps ->
                ps.setString(1, name); ps.setLong(2, uid); ps.executeUpdate()
            }
        }
        if (!old.isNullOrEmpty() && old != name) File("${Config.avatarsDir}/$old").delete()
        HttpUtil.writeJson(ctx, 200, mapOf("ok" to true, "avatar" to "/a/$name"))
    }

    fun channel(ctx: Context, name: String, viewer: Long) {
        val clean = name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        if (clean.isEmpty()) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        var uid = 0L
        var un = ""
        var ca = ""
        var av: String? = null
        var followers = 0L
        var nvideos = 0L
        var views = 0L
        var nV = 0L
        var nW = 0L
        var nM = 0L
        var following = false
        val ids = mutableListOf<Long>()
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id,username,avatar,created_at FROM users WHERE lower(username)=?").use { ps ->
                ps.setString(1, clean.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        uid = rs.getLong(1); un = rs.getString(2) ?: ""; av = rs.getString(3); ca = rs.getString(4) ?: ""
                        found = true
                    }
                }
            }
            if (!found) {
                HttpUtil.writeErr(ctx, 404, "Not found")
                return
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM follows WHERE followed_id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) followers = rs.getLong(1) }
            }
            Db.conn.prepareStatement("SELECT COUNT(*),COALESCE(SUM(views),0) FROM videos WHERE user_id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) { nvideos = rs.getLong(1); views = rs.getLong(2) } }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='video'").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) nV = rs.getLong(1) }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='wheel'").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) nW = rs.getLong(1) }
            }
            Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE user_id=? AND COALESCE(kind,'video')='music'").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> if (rs.next()) nM = rs.getLong(1) }
            }
            if (viewer >= 0 && viewer != uid) {
                Db.conn.prepareStatement("SELECT 1 FROM follows WHERE follower_id=? AND followed_id=?").use { ps ->
                    ps.setLong(1, viewer); ps.setLong(2, uid)
                    ps.executeQuery().use { rs -> following = rs.next() }
                }
            }
            Db.conn.prepareStatement("SELECT id FROM videos WHERE user_id=? ORDER BY id DESC LIMIT 120").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
            }
        }
        val avatar: Any? = if (!av.isNullOrEmpty()) "/a/$av" else null
        val user = mapOf(
            "id" to uid, "username" to un, "avatar" to avatar, "created_at" to ca,
            "followers" to followers, "videos" to nvideos, "views" to views, "following" to following,
            "counts" to mapOf("video" to nV, "wheel" to nW, "music" to nM)
        )
        val videos = mutableListOf<Any?>()
        for (id in ids) videos.add(HandlersCommon.videoJson(id, viewer))
        HttpUtil.writeJson(ctx, 200, mapOf("user" to user, "videos" to videos))
    }

    private fun readLimited(ctx: Context, max: Long): ByteArray? {
        return try {
            val inp = ctx.req().inputStream
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
