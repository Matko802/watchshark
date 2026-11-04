package watchshark

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

object Media {
    val transcodeSem = Semaphore(2)
    val bg = Executors.newCachedThreadPool { r ->
        Thread(r).also { it.isDaemon = true }
    }

    const val videoCapBytes: Long = 80L * 1024 * 1024

    fun dirSize(path: String): Long {
        var total = 0L
        try {
            Files.walk(Paths.get(path)).use { s ->
                s.filter { Files.isRegularFile(it) }.forEach {
                    try { total += Files.size(it) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return total
    }

    fun storedBytes(): Long = dirSize(Config.videosDir) + dirSize(Config.thumbsDir) + dirSize(Config.avatarsDir) + dirSize("${Config.dataDir}/users")

    fun runOut(name: String, vararg args: String, timeoutSec: Long = 60): Pair<String, Boolean> {
        return try {
            val pb = ProcessBuilder(listOf(name) + args)
            pb.redirectErrorStream(false)
            val p = pb.start()
            val out = StringBuilder()
            val t = Thread {
                try { p.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } } catch (_: Exception) {}
            }
            t.isDaemon = true
            t.start()
            val done = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!done) {
                p.destroyForcibly()
                Pair("", false)
            } else {
                t.join(2000)
                if (p.exitValue() == 0) Pair(out.toString().trim(), true) else Pair("", false)
            }
        } catch (_: Exception) {
            Pair("", false)
        }
    }

    fun probeHasVideo(path: String): Boolean {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", path)
        return ok && out.contains("video")
    }

    data class ProbeInfo(
        val vc: String, val ac: String, val fm: String,
        val dur: Double, val w: Long, val h: Long,
        val hasAudio: Boolean, val ok: Boolean
    )

    fun probeInfoOf(path: String): ProbeInfo {
        val (out, good) = runOut(
            "ffprobe", "-v", "error",
            "-show_entries", "stream=codec_name,codec_type,width,height:format=duration,format_name",
            "-of", "json", path
        )
        if (!good) return ProbeInfo("", "", "", 0.0, 0, 0, false, false)
        return try {
            val root = Auth.json.readTree(out)
            var vc = ""
            var ac = ""
            var w = 0L
            var h = 0L
            var hasAudio = false
            val streams = root.get("streams")
            if (streams != null && streams.isArray) {
                for (s in streams) {
                    when (s.get("codec_type")?.asText() ?: "") {
                        "video" -> if (vc.isEmpty()) {
                            vc = s.get("codec_name")?.asText() ?: ""
                            w = s.get("width")?.asLong() ?: 0L
                            h = s.get("height")?.asLong() ?: 0L
                        }
                        "audio" -> if (ac.isEmpty()) {
                            ac = s.get("codec_name")?.asText() ?: ""
                            hasAudio = true
                        }
                    }
                }
            }
            val fmt = root.get("format")
            var fm = ""
            var dur = 0.0
            if (fmt != null) {
                fm = (fmt.get("format_name")?.asText() ?: "").lowercase()
                dur = fmt.get("duration")?.asText("")?.toDoubleOrNull() ?: 0.0
                if (dur < 0) dur = 0.0
            }
            ProbeInfo(vc, ac, fm, dur, w, h, hasAudio, vc.isNotEmpty())
        } catch (_: Exception) {
            ProbeInfo("", "", "", 0.0, 0, 0, false, false)
        }
    }

    fun probeFormat(path: String): String {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-show_entries", "format=format_name", "-of", "csv=p=0", path)
        if (!ok) return ""
        return out.trim().lowercase()
    }

    fun probeDims(path: String): Triple<Long, Long, Boolean> {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", path)
        if (!ok) return Triple(0, 0, false)
        val parts = out.trim().split(",")
        if (parts.size != 2) return Triple(0, 0, false)
        val w = parts[0].trim().toLongOrNull() ?: 0
        val h = parts[1].trim().toLongOrNull() ?: 0
        if (w <= 0 || h <= 0) return Triple(0, 0, false)
        return Triple(w, h, true)
    }

    fun runFFmpeg(args: List<String>, timeout: java.time.Duration): Boolean {
        return try {
            val pb = ProcessBuilder(listOf("ffmpeg") + args)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val p = pb.start()
            val done = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!done) {
                p.destroyForcibly()
                false
            } else p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun setProgress(id: Long, stage: String, pct: Double) {
        try {
            synchronized(Db.lock) {
                Db.conn.prepareStatement("UPDATE videos SET stage=?, progress=? WHERE id=?").use { ps ->
                    ps.setString(1, stage)
                    ps.setDouble(2, pct.coerceIn(0.0, 100.0))
                    ps.setLong(3, id)
                    ps.executeUpdate()
                }
            }
        } catch (_: Exception) {}
    }

    fun runFFmpegProgress(id: Long, args: List<String>, dur: Double, stage: String, from: Double, to: Double): Boolean {
        return try {
            val pb = ProcessBuilder(listOf("ffmpeg", "-nostats", "-loglevel", "error", "-progress", "-") + args)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD)
            val p = pb.start()
            val reader = Thread {
                try {
                    var lastWrite = 0L
                    p.inputStream.bufferedReader().forEachLine { line ->
                        val ln = line.trim()
                        val secs = when {
                            ln.startsWith("out_time_us=") -> (ln.substringAfter('=').trim().toDoubleOrNull() ?: 0.0) / 1e6
                            ln.startsWith("out_time_ms=") -> (ln.substringAfter('=').trim().toDoubleOrNull() ?: 0.0) / 1e6
                            else -> return@forEachLine
                        }
                        if (dur <= 0) return@forEachLine
                        var frac = secs / dur
                        if (frac < 0) frac = 0.0
                        if (frac > 1) frac = 1.0
                        val now = System.currentTimeMillis()
                        if (now - lastWrite > 1000) {
                            lastWrite = now
                            setProgress(id, stage, from + (to - from) * frac)
                        }
                    }
                } catch (_: Exception) {}
            }
            reader.isDaemon = true
            reader.start()
            val done = p.waitFor(java.time.Duration.ofHours(4).toMillis(), TimeUnit.MILLISECONDS)
            if (!done) p.destroyForcibly()
            reader.join(2000)
            setProgress(id, stage, to)
            done && p.exitValue() == 0
        } catch (_: Exception) {
            setProgress(id, stage, to)
            false
        }
    }

    fun makeThumb(vpath: String, tpath: String): Boolean {
        val ok = runFFmpeg(
            listOf("-y", "-ss", "00:00:01", "-i", vpath, "-vframes", "1", "-vf", "scale=640:-1", "-c:v", "libwebp", "-q:v", "80", tpath),
            java.time.Duration.ofSeconds(60)
        )
        if (!ok) return false
        val f = File(tpath)
        return f.exists() && f.length() > 0
    }

    fun convertThumb(src: String, stem: String, outDir: java.io.File? = null): Pair<String, Boolean> {
        if (!probeHasVideo(src)) return Pair("", false)
        val name = "$stem.webp"
        val dir = outDir ?: java.io.File(Config.thumbsDir)
        try { dir.mkdirs() } catch (_: Exception) {}
        val out = java.io.File(dir, name).absolutePath
        if (!runFFmpeg(listOf("-y", "-i", src, "-vf", "scale=640:-1", "-c:v", "libwebp", "-q:v", "80", out), java.time.Duration.ofSeconds(120))) {
            File(out).delete()
            return Pair("", false)
        }
        if (fileSize(out) <= 0) {
            File(out).delete()
            return Pair("", false)
        }
        return Pair(name, true)
    }

    fun fileSize(path: String): Long {
        val f = File(path)
        if (!f.exists() || f.length() <= 0) return -1
        return f.length()
    }

    fun pfpSmallEnough(path: String): Boolean {
        val f = File(path)
        return f.exists() && f.length() > 0 && f.length() <= 10 * 1024 * 1024
    }

    fun transcode(inp: String, outp: String, dur: Double, target: Long, scale: String, id: Long = 0L): Boolean {
        transcodeSem.acquire()
        try {
            var vbr = 1500000L
            if (dur > 1.0) {
                vbr = ((target * 8.0 - 96000.0 * dur) / dur).toLong()
                if (vbr < 200000) vbr = 200000
                if (vbr > 8000000) vbr = 8000000
            }
            val vk = "${vbr / 1000}k"
            if (id <= 0L) {
                return runFFmpeg(
                    listOf("-y", "-i", inp, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", vk, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", outp),
                    java.time.Duration.ofHours(4)
                )
            }
            return runFFmpegProgress(
                id,
                listOf("-y", "-i", inp, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", vk, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", outp),
                dur, "compressing", 5.0, 85.0
            )
        } finally {
            transcodeSem.release()
        }
    }

    fun unlinkRenditions(fn: String) {
        var stem = fn
        val i = stem.lastIndexOf('.')
        if (i >= 0) stem = stem.substring(0, i)
        for (res in listOf("720p", "480p", "360p")) {
            try { Config.resolveVideo("$stem-$res.webm").delete() } catch (_: Exception) {}
        }
    }

    private val renditionLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun rendOne(src: String, dst: String, scale: String, br: String): Boolean {
        transcodeSem.acquire()
        try {
            return runFFmpeg(
                listOf("-y", "-i", src, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", br, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", dst),
                java.time.Duration.ofHours(4)
            )
        } finally {
            transcodeSem.release()
        }
    }

    private fun renditionSpec(res: Int): Pair<String, String>? = when (res) {
        720 -> "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" to "2500k"
        480 -> "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" to "1000k"
        360 -> "scale=640:360:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" to "500k"
        else -> null
    }

    fun ensureRendition(stem: String, res: Int): java.io.File? {
        val spec = renditionSpec(res) ?: return null
        val name = "$stem-${res}p.webm"
        val hit = Config.resolveVideo(name)
        if (hit.isFile) return hit
        val src = listOf("$stem.webm", "$stem.mp4")
            .map { Config.resolveVideo(it) }
            .firstOrNull { it.isFile } ?: return null
        val lock = renditionLocks.computeIfAbsent(name) { Any() }
        synchronized(lock) {
            try {
                val again = Config.resolveVideo(name)
                if (again.isFile) return again
                val (_, h, ok) = probeDims(src.absolutePath)
                if (!ok) return null
                if (h <= res) return src
                val dst = java.io.File(src.parentFile, name)
                if (!rendOne(src.absolutePath, dst.absolutePath, spec.first, spec.second)) {
                    try { dst.delete() } catch (_: Exception) {}
                    return null
                }
                if (!dst.isFile || dst.length() == 0L) {
                    try { dst.delete() } catch (_: Exception) {}
                    return null
                }
                mergeRenditionDb(stem, res, name)
                return dst
            } finally {
                renditionLocks.remove(name)
            }
        }
    }

    private fun mergeRenditionDb(stem: String, res: Int, name: String) {
        try {
            synchronized(Db.lock) {
                var id = 0L
                var cur: String? = null
                Db.conn.prepareStatement("SELECT id,renditions FROM videos WHERE filename=? OR filename=?").use { ps ->
                    ps.setString(1, "$stem.webm")
                    ps.setString(2, "$stem.mp4")
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            id = rs.getLong(1)
                            cur = rs.getString(2)
                        }
                    }
                }
                if (id == 0L) return
                val entry = "\"${res}p\":\"/v/$name\""
                val merged = if (cur.isNullOrBlank() || cur == "null") {
                    "{$entry}"
                } else {
                    val t = cur!!.trim()
                    if (t.contains("\"${res}p\"")) return
                    if (t.endsWith("}")) t.dropLast(1) + "," + entry + "}" else "{$entry}"
                }
                Db.conn.prepareStatement("UPDATE videos SET renditions=? WHERE id=?").use { ps ->
                    ps.setString(1, merged)
                    ps.setLong(2, id)
                    ps.executeUpdate()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun markFailed(id: Long) {
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET status='failed', stage='', progress=0 WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeUpdate()
            }
        }
    }

    fun notifyFollowers(id: Long, author: Long) {
        synchronized(Db.lock) {
            Db.conn.prepareStatement(
                "INSERT INTO notifications (user_id,video_id,kind) SELECT f.follower_id,?,'upload' FROM follows f JOIN users u ON u.id=f.follower_id WHERE f.followed_id=? AND f.follower_id!=? AND u.notify_uploads=1"
            ).use { ps ->
                ps.setLong(1, id)
                ps.setLong(2, author)
                ps.setLong(3, author)
                ps.executeUpdate()
            }
        }
    }

    fun processUpload(id: Long, author: Long, tmp: String, stem: String, customThumb: String) {
        var out = ""
        val thumbDir = Config.thumbsForVideoDir(java.io.File(tmp).parentFile ?: java.io.File(Config.videosDir))
        val th = java.io.File(thumbDir, "$stem.webp").absolutePath
        val thname = "$stem.webp"
        var fn = ""
        var mt = ""
        var size = 0L
        var good = false
        var fastKeep = false
        var vw = 0L
        var vh = 0L
        var thumbThread: Thread? = null
        var thumbMade = false
        val tmpF = File(tmp)
        if (tmpF.exists() && tmpF.length() > 0) {
            setProgress(id, "analyzing", 2.0)
            val pi = probeInfoOf(tmp)
            if (pi.ok) {
                if (customThumb.isEmpty()) {
                    thumbThread = Thread {
                        thumbMade = makeThumb(tmp, th)
                    }
                    thumbThread.isDaemon = true
                    thumbThread.start()
                }
                val webmish = pi.fm.contains("webm") || pi.fm.contains("matroska")
                val mp4ish = pi.fm.contains("mp4") || pi.fm.contains("mov") || pi.fm.contains("m4a") || pi.fm.contains("3gp")
                val fast = pi.dur > 0 && tmpF.length() <= videoCapBytes &&
                    ((webmish && (pi.vc == "av1" || pi.vc == "vp9")) || (mp4ish && pi.vc == "h264"))
                if (fast) {
                    var ext = "mp4"
                    var mime = "video/mp4"
                    if (webmish) {
                        ext = "webm"
                        mime = "video/webm"
                    }
                    fn = "$stem.$ext"
                    mt = mime
                    out = java.io.File(tmpF.parent, fn).absolutePath
                    if (tmpF.renameTo(File(out))) {
                        size = File(out).length()
                        vw = pi.w
                        vh = pi.h
                        good = true
                        fastKeep = true
                    }
                } else if (pi.dur > 0) {
                    setProgress(id, "compressing", 5.0)
                    fn = "$stem.webm"
                    mt = "video/webm"
                    out = java.io.File(tmpF.parent, fn).absolutePath
                    var scale = "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
                    if (pi.dur > 120.0) scale = "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
                    transcode(tmp, out, pi.dur, videoCapBytes - 2L * 1024 * 1024, scale, id)
                    var sz = fileSize(out)
                    if (sz < 0 || sz > videoCapBytes) {
                        File(out).delete()
                        transcode(tmp, out, pi.dur, 58L * 1024 * 1024, "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", id)
                        sz = fileSize(out)
                    }
                    tmpF.delete()
                    if (sz > 0 && sz <= videoCapBytes) {
                        size = sz
                        good = true
                    }
                }
            }
        }
        if (!good) {
            thumbThread?.let { try { it.join(5000) } catch (_: Exception) {} }
            if (thumbMade) try { File(th).delete() } catch (_: Exception) {}
            tmpF.delete()
            if (out.isNotEmpty()) File(out).delete()
            markFailed(id)
            return
        }
        setProgress(id, "finalizing", 88.0)
        var thumb: String? = null
        if (customThumb.isNotEmpty()) {
            val (name, ok) = convertThumb(customThumb, stem, thumbDir)
            if (ok) thumb = name
            File(customThumb).delete()
        } else {
            thumbThread?.join()
            if (thumbMade) thumb = thname
        }
        if (thumb == null && makeThumb(out, th)) thumb = thname
        if (vw == 0L || vh == 0L) {
            val (w, h, ok) = probeDims(out)
            if (ok) { vw = w; vh = h }
        }
        val ori = if (vh > vw) "v" else "h"
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET filename=?, size=?, thumbnail=?, mimetype=?, orientation=?, status='ready', stage='', progress=100 WHERE id=?").use { ps ->
                ps.setString(1, fn)
                ps.setLong(2, size)
                if (thumb == null) ps.setNull(3, java.sql.Types.VARCHAR) else ps.setString(3, thumb)
                ps.setString(4, mt)
                ps.setString(5, ori)
                ps.setLong(6, id)
                ps.executeUpdate()
            }
            var vis = "public"
            Db.conn.prepareStatement("SELECT COALESCE(visibility,'public') FROM videos WHERE id=?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> if (rs.next()) vis = rs.getString(1) ?: "public" }
            }
            if (vis != "private") notifyFollowers(id, author)
        }
    }

    fun backupOnce() {
        val dir = File("${Config.dataDir}/backups")
        dir.mkdirs()
        val dst = "${dir.absolutePath}/backup-${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.sqlite"
        if (!Db.backupTo(dst)) return
        val names = dir.listFiles { f -> f.name.startsWith("backup-") && f.isFile }?.map { it.name }?.sorted() ?: return
        var mutable = names.toMutableList()
        while (mutable.size > 24) {
            File(dir, mutable.removeAt(0)).delete()
        }
    }

    fun backupLoop() {
        bg.submit {
            backupOnce()
            while (true) {
                try { Thread.sleep(3600_000) } catch (_: InterruptedException) { return@submit }
                try { backupOnce() } catch (_: Exception) {}
            }
        }
    }

    fun writeMsmtprc() {
        if (Config.smtpHost.isEmpty()) return
        val content = "defaults\nauth on\ntls on\ntls_starttls on\ntls_trust_file /etc/ssl/certs/ca-certificates.crt\naccount default\nhost ${Config.smtpHost}\nport ${Config.smtpPort}\nfrom ${Config.smtpFrom}\nuser ${Config.smtpUser}\npassword ${Config.smtpPass}\n"
        try { File("/tmp/msmtprc").writeText(content) } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("chmod", "600", "/tmp/msmtprc")).waitFor() } catch (_: Exception) {}
    }

    fun sendMail(to: String, subject: String, body: String) {
        if (Config.smtpHost.isEmpty()) {
            println("[mail to=$to subject=$subject]\n$body")
            return
        }
        try {
            val pb = ProcessBuilder("msmtp", "-C", "/tmp/msmtprc", "--from=${Config.smtpFrom}", "-t")
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.outputStream.bufferedWriter().use {
                it.write("To: $to\nFrom: ${Config.smtpFrom}\nSubject: $subject\nContent-Type: text/plain; charset=utf-8\n\n$body\n")
            }
            p.waitFor(30, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    fun recoverJobs() {
        data class Row(val id: Long, val author: Long, val fn: String, val kind: String?)
        val rows = mutableListOf<Row>()
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT id,user_id,filename,kind FROM videos WHERE status='processing' LIMIT 256").use { rs ->
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        val author = rs.getLong(2)
                        val fn = rs.getString(3)
                        val kind = rs.getString(4)
                        if (fn.endsWith(".part")) rows.add(Row(id, author, fn, kind))
                        else Db.conn.prepareStatement("UPDATE videos SET status='failed' WHERE id=?").use { ps ->
                            ps.setLong(1, id); ps.executeUpdate()
                        }
                    }
                }
            }
        }
        for (r in rows) {
            val tmp = Config.resolveVideo(r.fn)
            if (!tmp.exists() || tmp.length() == 0L) {
                markFailed(r.id)
                continue
            }
            val stem = r.fn.removeSuffix(".part")
            bg.submit { processUpload(r.id, r.author, tmp.absolutePath, stem, "") }
        }
        val entries = File(Config.videosDir).walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.toList()
        for (e in entries) {
            var found = false
            synchronized(Db.lock) {
                Db.conn.prepareStatement("SELECT 1 FROM videos WHERE filename=? AND status='processing'").use { ps ->
                    ps.setString(1, e.name)
                    ps.executeQuery().use { rs -> found = rs.next() }
                }
            }
            if (!found) e.delete()
        }
    }

    fun sweepOrphans() {
        val keep = mutableSetOf<String>()
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT filename,thumbnail FROM videos").use { rs ->
                    while (rs.next()) {
                        val fn = rs.getString(1)
                        val th = rs.getString(2)
                        keep.add("v:$fn")
                        if (!th.isNullOrEmpty()) keep.add("t:$th")
                        val i = fn.lastIndexOf('.')
                        if (i > 0) {
                            val stem = fn.substring(0, i)
                            keep.add("v:${stem}-720p.webm")
                            keep.add("v:${stem}-480p.webm")
                            keep.add("v:${stem}-360p.webm")
                        }
                    }
                }
            }
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT avatar FROM users WHERE avatar IS NOT NULL").use { rs ->
                    while (rs.next()) {
                        val av = rs.getString(1)
                        if (!av.isNullOrEmpty()) keep.add("a:$av")
                    }
                }
            }
        }
        var removed = 0
        for ((dir, prefix) in listOf(Config.videosDir to "v:", Config.thumbsDir to "t:", Config.avatarsDir to "a:")) {
            val entries = try {
                File(dir).walkTopDown().filter { it.isFile }.toList()
            } catch (_: Exception) {
                continue
            }
            for (e in entries) {
                val nm = e.name
                if (nm.startsWith(".")) continue
                if (nm.endsWith(".part")) continue
                if (!keep.contains(prefix + nm)) {
                    if (e.delete()) removed++
                }
            }
        }
        if (removed > 0) println("[watchshark] removed $removed orphan files")
    }

    fun backfillOrientation() {
        var uv = 0L
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("PRAGMA user_version").use { rs ->
                    if (rs.next()) uv = rs.getLong(1)
                }
            }
        }
        if (uv >= 1) return
        data class Row(val id: Long, val fn: String)
        val rows = mutableListOf<Row>()
        synchronized(Db.lock) {
            Db.conn.createStatement().use { st ->
                st.executeQuery("SELECT id,filename FROM videos LIMIT 512").use { rs ->
                    while (rs.next()) rows.add(Row(rs.getLong(1), rs.getString(2)))
                }
            }
        }
        for (r in rows) {
            var o = "h"
            val (w, h, ok) = probeDims(Config.resolveVideo(r.fn).absolutePath)
            if (ok && h > w) o = "v"
            synchronized(Db.lock) {
                Db.conn.prepareStatement("UPDATE videos SET orientation=? WHERE id=?").use { ps ->
                    ps.setString(1, o); ps.setLong(2, r.id); ps.executeUpdate()
                }
            }
        }
        synchronized(Db.lock) {
            Db.conn.createStatement().use { it.execute("PRAGMA user_version=1") }
        }
    }
}
