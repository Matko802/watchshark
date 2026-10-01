package watchshark

import java.nio.file.Path
import java.nio.file.Paths

object Config {
    var dbPath: String = "data/db.sqlite"
    var dataDir: String = "data"
    var videosDir: String = "data/videos"
    var thumbsDir: String = "data/thumbs"
    var avatarsDir: String = "data/avatars"
    var publicDir: String = "public"
    var jwtSecret: String = "CHANGE_ME_watchshark_secret"
    var maxBytes: Long = 4096L * 1024 * 1024
    var quotaBytes: Long = 50L * 1024 * 1024 * 1024
    var adminUser: String = ""
    var appUrl: String = "https://watchshark.duckdns.org"
    var smtpHost: String = ""
    var smtpPort: String = "587"
    var smtpUser: String = ""
    var smtpPass: String = ""
    var smtpFrom: String = ""
    var port: Int = 3000

    fun envOr(k: String, d: String): String {
        val v = System.getenv(k)
        return if (v.isNullOrEmpty()) d else v
    }

    fun envULL(k: String, d: Long): Long {
        val v = System.getenv(k) ?: return d
        if (v.isEmpty()) return d
        return v.toULongOrNull()?.toLong() ?: d
    }

    fun load() {
        port = envOr("PORT", "3000").toIntOrNull() ?: 3000
        dataDir = envOr("DATA_DIR", "data")
        publicDir = envOr("PUBLIC_DIR", "public")
        videosDir = "$dataDir/videos"
        thumbsDir = "$dataDir/thumbs"
        avatarsDir = "$dataDir/avatars"
        jwtSecret = envOr("JWT_SECRET", "CHANGE_ME_watchshark_secret")
        if (System.getenv("JWT_SECRET").isNullOrEmpty()) {
            println("[watchshark] WARNING: JWT_SECRET not set, using default!")
        }
        maxBytes = envULL("MAX_UPLOAD_MB", 4096) * 1024 * 1024
        quotaBytes = envULL("QUOTA_GB", 50) * 1024 * 1024 * 1024
        adminUser = envOr("ADMIN_USER", "").lowercase()
        appUrl = envOr("APP_URL", "https://watchshark.duckdns.org")
        smtpHost = envOr("SMTP_HOST", "")
        smtpPort = envOr("SMTP_PORT", "587")
        smtpUser = envOr("SMTP_USER", "")
        smtpPass = envOr("SMTP_PASS", "")
        smtpFrom = envOr("SMTP_FROM", "")
        if (smtpFrom.isEmpty()) smtpFrom = smtpUser
        dbPath = "$dataDir/db.sqlite"
    }

    fun siteBase(): String = appUrl.trimEnd('/')

    /**
     * Per-user storage: data/users/<user>/videos|thumbs|avatars.
     * Legacy flat files (data/videos, kind subdirs, ...) keep working
     * through the resolvers below — no migration needed.
     */
    fun safeUserDir(username: String): String {
        val s = username.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(32)
        if (s.isEmpty()) return "u"
        return if (s in setOf("videos", "wheels", "music", "thumbs", "avatars")) "_$s" else s
    }

    fun userDir(username: String): java.io.File =
        java.io.File("$dataDir/users/${safeUserDir(username)}")

    fun userVideosDir(username: String): java.io.File =
        java.io.File(userDir(username), "videos").apply { mkdirs() }

    fun userThumbsDir(username: String): java.io.File =
        java.io.File(userDir(username), "thumbs").apply { mkdirs() }

    fun userAvatarsDir(username: String): java.io.File =
        java.io.File(userDir(username), "avatars").apply { mkdirs() }

    /** Thumbs dir matching a video dir (per-user layout or legacy flat). */
    fun thumbsForVideoDir(vdir: java.io.File): java.io.File {
        val p = vdir.parentFile
        if (vdir.name == "videos" && p != null && p.parentFile?.name == "users") {
            return java.io.File(p, "thumbs").apply { mkdirs() }
        }
        return java.io.File(thumbsDir)
    }

    private fun searchUserFiles(sub: String, clean: String): java.io.File? {
        val root = java.io.File("$dataDir/users")
        val users = try {
            root.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
        } catch (_: Exception) {
            return null
        }
        for (u in users) {
            val f = java.io.File(u, "$sub/$clean")
            if (f.isFile) return f
        }
        return null
    }

    /** Kind-separated storage: videos/ wheels/ music/ under videosDir. */
    fun videoKindDir(kind: String?): String = when (kind) {
        "wheel" -> "wheels"
        "music" -> "music"
        else -> "videos"
    }

    /** Upload target dir for a kind (created on demand). */
    fun videoDirFor(kind: String?): java.io.File =
        java.io.File("$videosDir/${videoKindDir(kind)}").apply { mkdirs() }

    /**
     * Resolve a stored video filename to its file. New uploads live in
     * kind subdirs; legacy files sit flat in videosDir — try subdirs
     * first, then the root, so old links keep working.
     */
    fun resolveVideo(name: String, kind: String? = null): java.io.File {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        searchUserFiles("videos", clean)?.let { return it }
        val dirs = (listOfNotNull(kind?.let { videoKindDir(it) }) + listOf("videos", "wheels", "music", ""))
            .distinct()
        for (d in dirs) {
            val f = if (d.isEmpty()) java.io.File(videosDir, clean) else java.io.File("$videosDir/$d", clean)
            if (f.isFile) return f
        }
        return java.io.File(videosDir, clean)
    }

    fun resolveThumb(name: String): java.io.File {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        searchUserFiles("thumbs", clean)?.let { return it }
        return java.io.File(thumbsDir, clean)
    }

    fun resolveAvatar(name: String): java.io.File {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        searchUserFiles("avatars", clean)?.let { return it }
        return java.io.File(avatarsDir, clean)
    }
}
