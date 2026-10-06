package watchshark

import io.javalin.http.Context

/**
 * Public Music API for custom clients (apps, bots, integrations).
 *
 *   GET /api/music?q=&sort=new|popular&page=&limit=
 *     -> { tracks: [...], page, pages, total }
 *   GET /api/music/{id}
 *     -> { track: {...} }
 *
 * A track is an upload with kind='music'. Reading needs no auth.
 * `plays` is an alias of `views`, `artist` an alias of `username`,
 * so clients can use whichever name they prefer.
 */
object MusicHandlers {
    private fun trackOf(id: Long, viewer: Long): Map<String, Any?>? {
        val v = HandlersCommon.videoJson(id, viewer) ?: return null
        if (v["kind"] != "music") return null
        val plays = (v["views"] as? Number)?.toLong() ?: 0L
        return mapOf(
            "id" to v["id"],
            "title" to v["title"],
            "description" to v["description"],
            "artist" to v["username"],
            "username" to v["username"],
            "user_id" to v["user_id"],
            "avatar" to v["avatar"],
            "src" to v["src"],
            "thumbnail" to v["thumbnail"],
            "mimetype" to v["mimetype"],
            "size" to v["size"],
            "plays" to plays,
            "views" to plays,
            "likes" to v["likes"],
            "liked" to v["liked"],
            "comments" to v["comments"],
            "followers" to v["followers"],
            "following" to v["following"],
            "status" to v["status"],
            "created_at" to v["created_at"],
            "kind" to "music"
        )
    }

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
        if (limit > 50) limit = 50
        val off = (page - 1) * limit
        val ids = mutableListOf<Long>()
        var total = 0L
        synchronized(Db.lock) {
            if (search.isEmpty()) {
                Db.conn.prepareStatement("SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')='music' ORDER BY $order LIMIT ? OFFSET ?").use { ps ->
                    ps.setLong(1, limit); ps.setLong(2, off)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
                }
                Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE COALESCE(kind,'video')='music'").use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) total = rs.getLong(1) }
                }
            } else {
                val like = "%${HttpUtil.escapeLike(search)}%"
                Db.conn.prepareStatement(
                    "SELECT v.id FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')='music' AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\') ORDER BY $order LIMIT ? OFFSET ?"
                ).use { ps ->
                    ps.setString(1, like); ps.setString(2, like); ps.setString(3, like)
                    ps.setLong(4, limit); ps.setLong(5, off)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong(1)) }
                }
                Db.conn.prepareStatement(
                    "SELECT COUNT(*) FROM videos v JOIN users u ON u.id=v.user_id WHERE COALESCE(v.kind,'video')='music' AND (v.title LIKE ? ESCAPE '\\' OR v.description LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')"
                ).use { ps ->
                    ps.setString(1, like); ps.setString(2, like); ps.setString(3, like)
                    ps.executeQuery().use { rs -> if (rs.next()) total = rs.getLong(1) }
                }
            }
        }
        val tracks = mutableListOf<Any?>()
        for (id in ids) {
            tracks.add(trackOf(id, viewer))
        }
        val pages = (total + limit - 1) / limit
        HttpUtil.writeJson(ctx, 200, mapOf("tracks" to tracks, "page" to page, "pages" to pages, "total" to total))
    }

    fun get(ctx: Context, id: Long) {
        var viewer = -1L
        val (aid, _, aok) = HandlersCommon.authUser(ctx)
        if (aok) viewer = aid
        var existsMusic = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT 1 FROM videos WHERE id=? AND COALESCE(kind,'video')='music'").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> existsMusic = rs.next() }
            }
            if (existsMusic && viewer >= 0) {
                HandlersCommon.recordViewLocked(id, viewer, "")
            }
        }
        if (!existsMusic) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val t = trackOf(id, viewer)
        if (t == null) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        HttpUtil.writeJson(ctx, 200, mapOf("track" to t))
    }
}
