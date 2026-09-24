package watchshark

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import java.io.File
import kotlin.system.exitProcess

val authLimiter = HttpUtil.RateLimiter(30, 3600_000L)
val apiLimiter = HttpUtil.RateLimiter(120, 60_000L)

val cleanPages = mapOf(
    "/watch" to "watch.html",
    "/channel" to "channel.html",
    "/wheels" to "wheels.html",
    "/music" to "music.html",
    "/upload" to "upload.html",
    "/settings" to "settings.html",
    "/forgot" to "forgot.html",
    "/reset" to "reset.html",
    "/verify" to "verify.html",
    "/admin" to "admin.html"
)

fun main() {
    Config.load()
    File(Config.dataDir).mkdirs()
    File(Config.videosDir).mkdirs()
    File(Config.thumbsDir).mkdirs()
    File(Config.avatarsDir).mkdirs()
    try {
        Db.openDb()
    } catch (e: Exception) {
        println("db open: $e")
        exitProcess(1)
    }
    Media.writeMsmtprc()
    Media.recoverJobs()
    Media.backfillOrientation()
    Media.sweepOrphans()
    Media.backupLoop()

    val app = Javalin.create { cfg ->
        cfg.http.defaultContentType = "application/json"
        cfg.http.maxRequestSize = Config.maxBytes + 64L * 1024 * 1024
        // Caddy already gzips at the edge. Javalin's compressor gzips bodies
        // without fixing the explicit Content-Length set by Static.serveMedia,
        // which truncates every compressed response and breaks all browsers.
        cfg.http.disableCompression()
    }

    app.before { ctx ->
        ctx.header("X-Content-Type-Options", "nosniff")
        val u = ctx.path()
        if (u.startsWith("/api/auth/")) {
            if (!authLimiter.allow(HttpUtil.clientIp(ctx))) {
                HttpUtil.writeErr(ctx, 429, "Rate limit exceeded, slow down")
                ctx.skipRemainingHandlers()
            }
        } else if (u.startsWith("/api/")) {
            if (!apiLimiter.allow(HttpUtil.clientIp(ctx))) {
                HttpUtil.writeErr(ctx, 429, "Rate limit exceeded, slow down")
                ctx.skipRemainingHandlers()
            }
        }
    }

    // ---- health ----
    app.get("/health") { ctx -> HttpUtil.writeJson(ctx, 200, mapOf("ok" to true)) }

    // ---- auth ----
    app.post("/api/auth/signup") { AuthHandlers.signup(it) }
    app.post("/api/auth/login") { AuthHandlers.login(it) }
    app.post("/api/auth/logout") { AuthHandlers.logout(it) }
    app.get("/api/auth/verify") { AuthHandlers.verify(it) }
    app.post("/api/auth/resend") { AuthHandlers.resend(it) }
    app.post("/api/auth/forgot") { AuthHandlers.forgot(it) }
    app.post("/api/auth/reset") { AuthHandlers.reset(it) }
    app.post("/api/auth/change") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else AuthHandlers.change(it, uid)
    }
    app.post("/api/auth/username") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else AuthHandlers.username(it, uid)
    }
    app.post("/api/settings/notifications") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else AuthHandlers.notifSet(it, uid)
    }
    app.get("/api/me") { AuthHandlers.me(it) }

    // ---- videos ----
    app.get("/api/videos") { VideoHandlers.list(it) }
    app.post("/api/videos") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else VideoHandlers.upload(it, uid)
    }
    app.get("/api/wheels") { VideoHandlers.wheels(it) }
    app.post("/api/pfp") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else VideoHandlers.pfp(it, uid)
    }
    app.get("/api/notifications") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else VideoHandlers.notifications(it, uid)
    }
    app.post("/api/notifications/read") {
        val (uid, _, ok) = HandlersCommon.authUser(it)
        if (!ok) HttpUtil.writeErr(it, 401, "Login required") else VideoHandlers.notifRead(it, uid)
    }

    // /api/videos/{id...} — use wildcard so /like /comments /thumbnail /edit suffixes match
    app.get("/api/videos/*") { ctx ->
        val u = ctx.path()
        val prefix = "/api/videos/"
        val rest = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        val (id, leftover, ok) = HttpUtil.parseId(rest)
        if (!ok || leftover != "") {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@get
        }
        VideoHandlers.getVideo(ctx, id)
    }
    app.delete("/api/videos/*") { ctx ->
        val u = ctx.path()
        val prefix = "/api/videos/"
        val rest = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        val (id, leftover, ok) = HttpUtil.parseId(rest)
        if (!ok || leftover != "") {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@delete
        }
        VideoHandlers.deleteVideo(ctx, id)
    }
    app.post("/api/videos/*") { ctx ->
        // handles /like /comments /thumbnail /edit suffixes; parse full path like Go.
        val u = ctx.path()
        val prefix = "/api/videos/"
        val rest = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        val (id, leftover, ok) = HttpUtil.parseId(rest)
        if (!ok) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@post
        }
        when (leftover) {
            "/like" -> VideoHandlers.like(ctx, id)
            "/comments" -> VideoHandlers.comment(ctx, id)
            "/thumbnail" -> VideoHandlers.thumb(ctx, id)
            "/edit" -> VideoHandlers.editVideo(ctx, id)
            else -> HttpUtil.writeErr(ctx, 404, "Not found")
        }
    }

    app.post("/api/follow/*") { ctx ->
        val u = ctx.path()
        val prefix = "/api/follow/"
        val rest = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        val (id, leftover, ok) = HttpUtil.parseId(rest)
        if (!ok || leftover != "") {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@post
        }
        val (uid, _, aok) = HandlersCommon.authUser(ctx)
        if (!aok) {
            HttpUtil.writeErr(ctx, 401, "Login required")
            return@post
        }
        VideoHandlers.follow(ctx, uid, id)
    }
    app.get("/api/channel/*") { ctx ->
        var viewer = -1L
        val (aid, _, aok) = HandlersCommon.authUser(ctx)
        if (aok) viewer = aid
        val u = ctx.path()
        val prefix = "/api/channel/"
        val name = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        if (name.isEmpty()) {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@get
        }
        VideoHandlers.channel(ctx, name, viewer)
    }

    // ---- admin ----
    app.get("/api/admin/pending") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.pending(it)
    }
    app.post("/api/admin/approve") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.approve(it)
    }
    app.post("/api/admin/reject") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.reject(it)
    }
    app.get("/api/admin/users") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.users(it)
    }
    app.post("/api/admin/ban") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.ban(it)
    }
    app.post("/api/admin/unban") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.unban(it)
    }
    app.post("/api/admin/soft-delete") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.softDelete(it)
    }
    app.post("/api/admin/restore") {
        val (_, _, ok) = AdminHandlers.requireAdmin(it)
        if (ok) AdminHandlers.restore(it)
    }
    app.delete("/api/admin/users/*") { ctx ->
        val (uid, _, ok) = AdminHandlers.requireAdmin(ctx)
        if (!ok) return@delete
        val u = ctx.path()
        val prefix = "/api/admin/users/"
        val rest = if (u.startsWith(prefix)) u.substring(prefix.length) else ""
        val (id, leftover, pok) = HttpUtil.parseId(rest)
        if (!pok || leftover != "") {
            HttpUtil.writeErr(ctx, 404, "Not found")
            return@delete
        }
        AdminHandlers.delUser(ctx, id, uid)
    }

    // ---- 404 for unknown api ----
    app.before("/api/*") { ctx ->
        // handled per-route; this only sets JSON 404 if no route matched — done via error handler below
    }
    app.error(404) { ctx ->
        if (ctx.path().startsWith("/api/") && ctx.result().isNullOrEmpty()) {
            ctx.header("Content-Type", "application/json")
            ctx.result("{\"error\":\"Not found\"}")
        }
    }

    // ---- clean pages, redirects, crawler meta, static ----
    app.get("/shorts") { ctx ->
        var dest = "/wheels"
        val qs = ctx.req().queryString
        if (!qs.isNullOrEmpty()) dest += "?$qs"
        ctx.redirect(dest, HttpStatus.MOVED_PERMANENTLY)
    }

    for ((route, file) in cleanPages) {
        app.get(route) { ctx ->
            // crawler meta for /watch and /channel
            if (Static.isCrawler(ctx)) {
                if (route == "/watch") {
                    val q = ctx.queryParam("id") ?: ""
                    val (id, rest, ok) = HttpUtil.parseId(q)
                    if (ok && rest == "") {
                        val tags = Static.watchMeta(id)
                        if (tags.isNotEmpty()) {
                            Static.servePageWithMeta(ctx, "watch.html", tags)
                            return@get
                        }
                    }
                }
                if (route == "/channel") {
                    val tags = Static.channelMeta(ctx.queryParam("user") ?: "")
                    if (tags.isNotEmpty()) {
                        Static.servePageWithMeta(ctx, "channel.html", tags)
                        return@get
                    }
                }
            }
            Static.serveMedia(ctx, "${Config.publicDir}/$file", file)
        }
    }

    // /x.html -> /x canonical redirect
    app.get("/*.html") { ctx ->
        val p = ctx.path() // e.g. /watch.html
        val name = p.removePrefix("/").removeSuffix(".html")
        if (cleanPages.containsKey("/$name")) {
            var dest = "/$name"
            val qs = ctx.req().queryString
            if (!qs.isNullOrEmpty()) dest += "?$qs"
            ctx.redirect(dest, HttpStatus.MOVED_PERMANENTLY)
        } else {
            Static.serveStatic(ctx, p)
        }
    }

    // ---- static fallback (GET + HEAD) ----
    val staticHandler = { ctx: Context -> Static.serveStatic(ctx, ctx.path()) }
    app.get("/*") { ctx ->
        if (ctx.path().startsWith("/api/")) {
            HttpUtil.writeErr(ctx, 404, "Not found")
        } else staticHandler(ctx)
    }
    app.head("/*") { ctx ->
        if (ctx.path().startsWith("/api/")) {
            HttpUtil.writeErr(ctx, 404, "Not found")
        } else staticHandler(ctx)
    }

    app.start(Config.port)
    println("[watchshark] (kotlin) up on :${Config.port} data=${Config.dataDir}")

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop()
    })
}
