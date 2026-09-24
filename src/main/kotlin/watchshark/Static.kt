package watchshark

import io.javalin.http.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder

object Static {
    fun extOf(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i >= 0) name.substring(i).lowercase() else ""
    }

    fun serveMedia(ctx: Context, full: String, name: String, immutable: Boolean = false) {
        val f = File(full)
        if (!f.exists() || !f.isFile) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val ct = HttpUtil.mediaTypes[extOf(name)] ?: "application/octet-stream"
        val len = f.length()
        val range = ctx.header("Range")
        ctx.header("Content-Type", ct)
        ctx.header("Accept-Ranges", "bytes")
        ctx.header("X-Content-Type-Options", "nosniff")
        // Media filenames are content hashes — immutable forever, safe to
        // cache hard (YouTube-style edge/client caching for instant revisits).
        // Everything else (HTML/CSS/JS) must revalidate so edits go live
        // on plain refresh without rebuilds or hard-refreshes.
        if (immutable) {
            ctx.header("Cache-Control", "public, max-age=31536000, immutable")
        } else {
            ctx.header("Cache-Control", "no-cache")
        }
        val isHead = ctx.method().name == "HEAD"
        if (isHead) {
            // headers only; still need Content-Length
            if (range == null) {
                ctx.header("Content-Length", len.toString())
                ctx.status(200)
                return
            }
        }
        if (range != null && range.startsWith("bytes=")) {
            try {
                val spec = range.removePrefix("bytes=").split(",")[0].trim()
                var start = 0L
                var end = len - 1
                if (spec.startsWith("-")) {
                    val suffix = spec.substring(1).toLong()
                    if (suffix > 0) start = maxOf(0, len - suffix)
                } else if (spec.contains("-")) {
                    val p = spec.split("-", limit = 2)
                    start = p[0].toLong()
                    if (p[1].isNotEmpty()) end = p[1].toLong()
                }
                if (start < 0) start = 0
                if (end >= len) end = len - 1
                if (start > end || start >= len) {
                    ctx.header("Content-Range", "bytes */$len")
                    ctx.status(416)
                    return
                }
                val count = end - start + 1
                ctx.header("Content-Range", "bytes $start-$end/$len")
                ctx.header("Content-Length", count.toString())
                ctx.status(206)
                if (isHead) return
                val raf = RandomAccessFile(f, "r")
                raf.seek(start)
                val bounded = object : java.io.InputStream() {
                    var remaining = count
                    override fun read(): Int {
                        if (remaining <= 0) return -1
                        val b = raf.read()
                        if (b >= 0) remaining--
                        if (remaining < 0) { raf.close() }
                        return b
                    }
                    override fun read(b: ByteArray, off: Int, l: Int): Int {
                        if (remaining <= 0) {
                            raf.close()
                            return -1
                        }
                        val n = raf.read(b, off, minOf(l.toLong(), remaining).toInt())
                        if (n > 0) remaining -= n
                        if (remaining <= 0 || n < 0) { try { raf.close() } catch (_: Exception) {} }
                        return n
                    }
                    override fun close() {
                        try { raf.close() } catch (_: Exception) {}
                    }
                }
                ctx.result(bounded)
                return
            } catch (_: Exception) {
                // fall through to full serve
            }
        }
        ctx.header("Content-Length", len.toString())
        ctx.status(200)
        if (isHead) return
        ctx.result(f.inputStream())
    }

    fun serveStatic(ctx: Context, uri: String) {
        if (uri == "/") {
            serveMedia(ctx, "${Config.publicDir}/index.html", "index.html")
            return
        }
        if (uri.startsWith("/v/") || uri.startsWith("/t/") || uri.startsWith("/a/")) {
            val nm = URLDecoder.decode(uri.substring(3), "UTF-8")
            if (!HttpUtil.cleanName(nm)) {
                HttpUtil.writeErr(ctx, 404, "Not found")
                return
            }
            if (uri[1] == 'v') {
                // Dynamic rendition: /v/<stem>-<360|480|720>p.webm generates
                // on first request (cached on disk afterwards).
                val m = Regex("""^(.+)-(720|480|360)p\.webm$""").matchEntire(nm)
                if (m != null) {
                    val f = Media.ensureRendition(m.groupValues[1], m.groupValues[2].toInt())
                    if (f == null || !f.isFile) {
                        HttpUtil.writeErr(ctx, 404, "Not found")
                        return
                    }
                    serveMedia(ctx, f.absolutePath, nm, true)
                    return
                }
                val f = Config.resolveVideo(nm)
                serveMedia(ctx, f.absolutePath, nm, true)
                return
            }
            val base = if (uri[1] == 't') Config.thumbsDir else Config.avatarsDir
            serveMedia(ctx, "$base/$nm", nm, true)
            return
        }
        if (uri.contains("..")) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        val rel = uri.trimStart('/').split("/").joinToString(File.separator)
        val full = File(Config.publicDir, rel).canonicalPath
        val pubCanon = File(Config.publicDir).canonicalPath
        if (!full.startsWith(pubCanon)) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return
        }
        serveMedia(ctx, full, File(full).name)
    }

    private val attrEsc = mapOf("&" to "&amp;", "<" to "&lt;", ">" to "&gt;", "\"" to "&quot;")

    fun escAttr(s: String): String {
        var r = s
        for ((k, v) in attrEsc) r = r.replace(k, v)
        return r
    }

    fun metaTag(prop: String, content: String): String {
        if (content.isEmpty()) return ""
        return "<meta property=\"$prop\" content=\"${escAttr(content)}\">\n"
    }

    fun servePageWithMeta(ctx: Context, file: String, tags: String) {
        val f = File("${Config.publicDir}/$file")
        if (!f.exists()) {
            serveMedia(ctx, f.absolutePath, file)
            return
        }
        ctx.header("Content-Type", "text/html; charset=utf-8")
        ctx.header("X-Content-Type-Options", "nosniff")
        ctx.status(200)
        if (ctx.method().name == "HEAD") return
        val html = f.readText()
        ctx.result(html.replace("</head>", tags + "</head>"))
    }

    fun isCrawler(ctx: Context): Boolean {
        val ua = (ctx.header("User-Agent") ?: "").lowercase()
        for (s in listOf("whatsapp", "discordbot", "twitterbot", "facebookexternalhit", "telegrambot", "slackbot", "linkedinbot", "pinterest", "googlebot")) {
            if (ua.contains(s)) return true
        }
        return false
    }

    fun watchMeta(id: Long): String {
        val v = HandlersCommon.videoJson(id, -1) ?: return ""
        fun str(k: String): String = v[k] as? String ?: ""
        var title = str("title")
        if (title.isEmpty()) title = "WatchShark video"
        var desc = HttpUtil.truncateRunes(str("description"), 200)
        if (desc.isEmpty()) {
            val viewsStr = (v["views"] as? Number)?.let { "${it.toLong()} views" } ?: ""
            var by = str("username")
            if (by.isNotEmpty()) by = "@$by"
            desc = "$by • $viewsStr".trim().trim('•', ' ')
            if (desc.isEmpty()) desc = title
        }
        val kind = str("kind")
        val src = str("src")
        val thumb = v["thumbnail"] as? String ?: ""
        val sb = StringBuilder()
        sb.append(metaTag("og:site_name", "WatchShark"))
        sb.append("<meta name=\"twitter:card\" content=\"summary_large_image\">\n")
        sb.append(metaTag("og:url", "${Config.siteBase()}/watch?id=$id"))
        sb.append(metaTag("og:title", title))
        sb.append(metaTag("og:description", desc))
        if (kind == "music") {
            sb.append(metaTag("og:type", "music.song"))
            if (src.isNotEmpty()) {
                sb.append(metaTag("og:audio", Config.siteBase() + src))
                sb.append(metaTag("og:audio:type", "audio/ogg"))
            }
            sb.append(metaTag("og:image", if (thumb.isNotEmpty()) Config.siteBase() + thumb else Config.siteBase() + "/watchshark.webp"))
            return sb.toString()
        }
        sb.append(metaTag("og:type", "video.other"))
        sb.append(metaTag("og:image", if (thumb.isNotEmpty()) Config.siteBase() + thumb else Config.siteBase() + "/watchshark.webp"))
        if (src.isNotEmpty()) {
            sb.append(metaTag("og:video", Config.siteBase() + src))
            sb.append(metaTag("og:video:secure_url", Config.siteBase() + src))
            var mt = str("mimetype")
            if (mt.isEmpty()) mt = "video/mp4"
            sb.append(metaTag("og:video:type", mt))
            val (w, h, ok) = Media.probeDims(Config.resolveVideo(src.removePrefix("/v/")).absolutePath)
            if (ok) {
                sb.append(metaTag("og:video:width", w.toString()))
                sb.append(metaTag("og:video:height", h.toString()))
            }
        }
        return sb.toString()
    }

    fun channelMeta(name: String): String {
        val clean = name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        if (clean.isEmpty()) return ""
        var uid = 0L
        var un = ""
        var av: String? = null
        var followers = 0L
        var nvideos = 0L
        var found = false
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT id,username,avatar FROM users WHERE lower(username)=?").use { ps ->
                ps.setString(1, clean.lowercase())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        uid = rs.getLong(1); un = rs.getString(2) ?: ""; av = rs.getString(3)
                        found = true
                    }
                }
            }
            if (found) {
                Db.conn.prepareStatement("SELECT COUNT(*) FROM follows WHERE followed_id=?").use { ps ->
                    ps.setLong(1, uid)
                    ps.executeQuery().use { rs -> if (rs.next()) followers = rs.getLong(1) }
                }
                Db.conn.prepareStatement("SELECT COUNT(*) FROM videos WHERE user_id=?").use { ps ->
                    ps.setLong(1, uid)
                    ps.executeQuery().use { rs -> if (rs.next()) nvideos = rs.getLong(1) }
                }
            }
        }
        if (!found) return ""
        val img = if (!av.isNullOrEmpty()) "${Config.siteBase()}/a/$av" else "${Config.siteBase()}/watchshark.webp"
        val sb = StringBuilder()
        sb.append(metaTag("og:site_name", "WatchShark"))
        sb.append(metaTag("og:type", "website"))
        sb.append("<meta name=\"twitter:card\" content=\"summary_large_image\">\n")
        sb.append(metaTag("og:url", "${Config.siteBase()}/channel?user=$un"))
        sb.append(metaTag("og:title", "@$un on WatchShark"))
        sb.append(metaTag("og:description", "$nvideos videos • $followers followers on WatchShark."))
        sb.append(metaTag("og:image", img))
        return sb.toString()
    }
}
