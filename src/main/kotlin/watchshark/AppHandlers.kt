package watchshark

import io.javalin.http.Context
import java.net.HttpURLConnection
import java.net.URL

object AppHandlers {
    private const val REPO = "Matko802/watchshark"
    private const val LATEST_URL = "https://github.com/Matko802/watchshark/releases/latest"
    private const val TAG_PREFIX = "https://github.com/Matko802/watchshark/releases/tag/"
    private const val CACHE_MS = 10 * 60 * 1000L

    @Volatile
    private var cachedAt = 0L

    @Volatile
    private var cached: Map<String, Any?>? = null

    private fun headSize(url: String): Long {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.requestMethod = "HEAD"
            c.setRequestProperty("User-Agent", "WatchShark-server")
            c.connect()
            val len = c.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
            c.disconnect()
            len
        } catch (_: Exception) {
            0L
        }
    }

    private fun lookup(): Map<String, Any?>? {
        return try {
            val c = URL(LATEST_URL).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "WatchShark-server")
            c.connect()
            val code = c.responseCode
            val loc = c.getHeaderField("Location") ?: ""
            c.disconnect()
            if ((code == 301 || code == 302) && loc.startsWith(TAG_PREFIX)) {
                val tag = loc.removePrefix(TAG_PREFIX).substringBefore('/').substringBefore('?')
                if (!tag.startsWith("android-v")) return null
                val version = tag.removePrefix("android-v")
                val apk = "https://github.com/$REPO/releases/download/$tag/WatchShark-$version.apk"
                mapOf(
                    "tag" to tag,
                    "version" to version,
                    "apk_url" to apk,
                    "size" to headSize(apk),
                    "html_url" to "https://github.com/$REPO/releases/tag/$tag",
                    "releases_url" to "https://github.com/$REPO/releases"
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun latest(ctx: Context) {
        val now = System.currentTimeMillis()
        var data = cached
        if (data == null || now - cachedAt > CACHE_MS) {
            data = lookup()
            if (data != null) {
                cached = data
                cachedAt = now
            } else if (cached != null) {
                data = cached
            }
        }
        if (data == null) {
            HttpUtil.writeJson(
                ctx, 502, mapOf(
                    "error" to "Could not reach GitHub releases",
                    "releases_url" to "https://github.com/$REPO/releases"
                )
            )
            return
        }
        HttpUtil.writeJson(ctx, 200, data)
    }
}
