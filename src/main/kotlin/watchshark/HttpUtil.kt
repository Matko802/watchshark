package watchshark

import io.javalin.http.Context
import java.util.concurrent.ConcurrentHashMap

object HttpUtil {
    val mediaTypes = mapOf(
        ".webm" to "video/webm",
        ".ogg" to "audio/ogg",
        ".opus" to "audio/ogg",
        ".mp3" to "audio/mpeg",
        ".m4a" to "audio/mp4",
        ".flac" to "audio/flac",
        ".wav" to "audio/wav",
        ".mkv" to "video/x-matroska",
        ".mp4" to "video/mp4",
        ".avi" to "video/x-msvideo",
        ".mov" to "video/quicktime",
        ".ts" to "video/mp2t",
        ".flv" to "video/x-flv",
        ".wmv" to "video/x-ms-wmv",
        ".mpg" to "video/mpeg",
        ".mpeg" to "video/mpeg",
        ".ogv" to "video/ogg",
        ".3gp" to "video/3gpp",
        ".webp" to "image/webp",
        ".jpg" to "image/jpeg",
        ".jpeg" to "image/jpeg",
        ".png" to "image/png",
        ".gif" to "image/gif",
        ".html" to "text/html; charset=utf-8",
        ".css" to "text/css; charset=utf-8",
        ".js" to "text/javascript; charset=utf-8",
        ".json" to "application/json",
        ".txt" to "text/plain; charset=utf-8",
        ".ico" to "image/x-icon"
    )

    fun writeJson(ctx: Context, code: Int, v: Any) {
        ctx.status(code)
        ctx.header("Content-Type", "application/json")
        ctx.header("X-Content-Type-Options", "nosniff")
        ctx.json(v)
    }

    fun writeErr(ctx: Context, code: Int, msg: String) {
        writeJson(ctx, code, mapOf("error" to msg))
    }

    fun clientIp(ctx: Context): String {
        val fwd = ctx.header("X-Forwarded-For") ?: ""
        if (fwd.isNotEmpty()) {
            val i = fwd.indexOf(',')
            val first = if (i >= 0) fwd.substring(0, i) else fwd
            val ip = first.trim()
            if (ip.isNotEmpty()) return ip
        }
        val remote = ctx.req().remoteAddr ?: return "unknown"
        return remote
    }

    fun cleanName(nm: String): Boolean {
        if (nm.isEmpty() || nm.length > 128 || nm[0] == '.') return false
        if (nm.contains("/") || nm.contains("..")) return false
        return true
    }

    /** Parse leading positive int id, return Triple(id, rest, ok) */
    fun parseId(s: String): Triple<Long, String, Boolean> {
        var i = 0
        while (i < s.length && s[i] in '0'..'9') i++
        if (i == 0) return Triple(0, s, false)
        val id = s.substring(0, i).toLongOrNull() ?: return Triple(0, s, false)
        if (id <= 0) return Triple(0, s, false)
        return Triple(id, s.substring(i), true)
    }

    fun truncateRunes(s: String, n: Int): String {
        var t = s.trim()
        val cps = t.codePoints().toArray()
        if (cps.size > n) {
            t = String(cps.copyOf(n), 0, n).trim()
        }
        return t.trim()
    }

    fun escapeLike(s: String): String {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }

    class RateLimiter(val max: Int, val windowMs: Long) {
        data class Entry(var count: Int, var reset: Long)
        private val hits = ConcurrentHashMap<String, Entry>()

        fun allow(ip: String): Boolean {
            val now = System.currentTimeMillis()
            val e = hits[ip]
            if (e == null || now - e.reset >= windowMs) {
                hits[ip] = Entry(1, now)
                return true
            }
            e.count++
            return e.count <= max
        }
    }
}
