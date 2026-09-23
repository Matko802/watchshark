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

    fun storedBytes(): Long = dirSize(Config.videosDir) + dirSize(Config.thumbsDir) + dirSize(Config.avatarsDir)

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

    fun probeHasAudio(path: String): Boolean {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "csv=p=0", path)
        return ok && out.contains("audio")
    }

    fun probeDuration(path: String): Double {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path)
        if (!ok) return 0.0
        val d = out.trim().toDoubleOrNull() ?: 0.0
        return if (d < 0) 0.0 else d
    }

    fun probeFormat(path: String): String {
        val (out, ok) = runOut("ffprobe", "-v", "error", "-show_entries", "format=format_name", "-of", "csv=p=0", path)
        if (!ok) return ""
        return out.trim().lowercase()
    }

    data class AV(val vc: String, val ac: String, val fm: String, val ok: Boolean)

    fun probeAV(path: String): AV {
        val (out, good) = runOut(
            "ffprobe", "-v", "error",
            "-show_entries", "stream=codec_name,codec_type",
            "-show_entries", "format=format_name",
            "-of", "csv=p=0", path
        )
        if (!good) return AV("", "", "", false)
        var vc = ""
        var ac = ""
        var fm = ""
        for (raw in out.split("\n")) {
            val ln = raw.trim('"', ' ', '\r')
            when {
                ln.contains(",video") -> {
                    val i = ln.indexOf(',')
                    if (i > 0) vc = ln.substring(0, i)
                }
                ln.contains(",audio") -> {
                    val i = ln.indexOf(',')
                    if (i > 0) ac = ln.substring(0, i)
                }
                ln.isNotEmpty() -> fm = ln
            }
        }
        return AV(vc, ac, fm, vc.isNotEmpty())
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

    fun makeThumb(vpath: String, tpath: String): Boolean {
        val ok = runFFmpeg(
            listOf("-y", "-ss", "00:00:01", "-i", vpath, "-vframes", "1", "-vf", "scale=640:-1", "-c:v", "libwebp", "-q:v", "80", tpath),
            java.time.Duration.ofSeconds(60)
        )
        if (!ok) return false
        val f = File(tpath)
        return f.exists() && f.length() > 0
    }

    fun convertThumb(src: String, stem: String): Pair<String, Boolean> {
        if (!probeHasVideo(src)) return Pair("", false)
        val name = "$stem.webp"
        val out = "${Config.thumbsDir}/$name"
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

    fun transcode(inp: String, outp: String, dur: Double, target: Long, scale: String): Boolean {
        transcodeSem.acquire()
        try {
            var vbr = 1500000L
            if (dur > 1.0) {
                vbr = ((target * 8.0 - 96000.0 * dur) / dur).toLong()
                if (vbr < 200000) vbr = 200000
                if (vbr > 8000000) vbr = 8000000
            }
            val vk = "${vbr / 1000}k"
            return runFFmpeg(
                listOf("-y", "-i", inp, "-map", "0:v:0", "-map", "0:a?", "-vf", scale, "-c:v", "libsvtav1", "-preset", "12", "-b:v", vk, "-c:a", "libopus", "-b:a", "96k", "-f", "webm", outp),
                java.time.Duration.ofHours(4)
            )
        } finally {
            transcodeSem.release()
        }
    }

    fun unlinkRenditions(fn: String) {
        var stem = fn
        val i = stem.lastIndexOf('.')
        if (i >= 0) stem = stem.substring(0, i)
        File("${Config.videosDir}/${stem}-720p.webm").delete()
        File("${Config.videosDir}/${stem}-480p.webm").delete()
        File("${Config.videosDir}/${stem}-360p.webm").delete()
    }

    fun spawnRenditions(id: Long, stem: String, src: String, h: Long) {
        if (h <= 480) return
        bg.submit { processRenditions(id, stem, src, h) }
    }

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

    fun processRenditions(id: Long, stem: String, src: String, h: Long) {
        val p720 = "${Config.videosDir}/${stem}-720p.webm"
        val p480 = "${Config.videosDir}/${stem}-480p.webm"
        val p360 = "${Config.videosDir}/${stem}-360p.webm"
        var has720 = false
        var has480 = false
        var has360 = false
        if (h > 720) has720 = rendOne(src, p720, "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "2500k")
        if (h > 480) has480 = rendOne(src, p480, "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "1000k")
        if (h > 360) has360 = rendOne(src, p360, "scale=640:360:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2", "500k")
        if (!has720 && !has480 && !has360) return
        val parts = mutableListOf<String>()
        if (has720 && File(p720).exists()) parts.add("\"720p\":\"/v/${stem}-720p.webm\"")
        if (has480 && File(p480).exists()) parts.add("\"480p\":\"/v/${stem}-480p.webm\"")
        if (has360 && File(p360).exists()) parts.add("\"360p\":\"/v/${stem}-360p.webm\"")
        if (parts.isEmpty()) return
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET renditions=? WHERE id=?").use { ps ->
                ps.setString(1, "{${parts.joinToString(",")}}")
                ps.setLong(2, id)
                ps.executeUpdate()
            }
        }
    }

    fun markFailed(id: Long) {
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET status='failed' WHERE id=?").use { ps ->
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
        val th = "${Config.thumbsDir}/$stem.webp"
        val thname = "$stem.webp"
        var fn = ""
        var mt = ""
        var size = 0L
        var good = false
        val tmpF = File(tmp)
        if (tmpF.exists() && tmpF.length() > 0) {
            val av = probeAV(tmp)
            if (av.ok) {
                if (av.vc == "av1" && (av.ac == "" || av.ac == "opus") && (av.fm.contains("webm") || av.fm.contains("matroska")) && tmpF.length() <= 100L * 1024 * 1024) {
                    fn = "$stem.webm"
                    mt = "video/webm"
                    out = "${Config.videosDir}/$fn"
                    if (tmpF.renameTo(File(out))) {
                        size = File(out).length()
                        good = true
                    }
                } else {
                    val dur = probeDuration(tmp)
                    fn = "$stem.webm"
                    mt = "video/webm"
                    out = "${Config.videosDir}/$fn"
                    var scale = "scale=1280:720:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
                    if (dur > 120.0) scale = "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2"
                    transcode(tmp, out, dur, 95L * 1024 * 1024, scale)
                    var sz = fileSize(out)
                    if (sz < 0 || sz > 100L * 1024 * 1024) {
                        File(out).delete()
                        transcode(tmp, out, dur, 80L * 1024 * 1024, "scale=854:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2")
                    }
                    tmpF.delete()
                    sz = fileSize(out)
                    if (sz > 0 && sz <= 100L * 1024 * 1024) {
                        size = sz
                        good = true
                    }
                }
            }
        }
        if (!good) {
            tmpF.delete()
            if (out.isNotEmpty()) File(out).delete()
            markFailed(id)
            return
        }
        var thumb: String? = null
        if (customThumb.isNotEmpty()) {
            val (name, ok) = convertThumb(customThumb, stem)
            if (ok) thumb = name
            File(customThumb).delete()
        }
        if (thumb == null && makeThumb(out, th)) thumb = thname
        val (vw, vh, _) = probeDims(out)
        val ori = if (vh > vw) "v" else "h"
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET filename=?, size=?, thumbnail=?, mimetype=?, orientation=?, status='ready' WHERE id=?").use { ps ->
                ps.setString(1, fn)
                ps.setLong(2, size)
                if (thumb == null) ps.setNull(3, java.sql.Types.VARCHAR) else ps.setString(3, thumb)
                ps.setString(4, mt)
                ps.setString(5, ori)
                ps.setLong(6, id)
                ps.executeUpdate()
            }
        }
        notifyFollowers(id, author)
        if (vh > 480) spawnRenditions(id, stem, out, vh)
    }

    fun processMusic(id: Long, author: Long, tmp: String, stem: String, customThumb: String) {
        val out = "${Config.videosDir}/$stem.ogg"
        val th = "${Config.thumbsDir}/$stem.webp"
        val thname = "$stem.webp"
        val tmpF = File(tmp)
        if (!tmpF.exists() || tmpF.length() == 0L) {
            tmpF.delete()
            markFailed(id)
            return
        }
        if (!probeHasAudio(tmp)) {
            tmpF.delete()
            markFailed(id)
            return
        }
        val ok = runFFmpeg(listOf("-y", "-i", tmp, "-map", "0:a", "-c:a", "libopus", "-b:a", "128k", out), java.time.Duration.ofMinutes(30))
        tmpF.delete()
        val size = fileSize(out)
        if (!ok || size <= 0 || size > 100L * 1024 * 1024) {
            File(out).delete()
            markFailed(id)
            return
        }
        var thumb: String? = null
        if (customThumb.isNotEmpty()) {
            val (name, good) = convertThumb(customThumb, stem)
            if (good) thumb = name
            File(customThumb).delete()
        }
        if (thumb == null && runFFmpeg(
                listOf("-y", "-i", out, "-filter_complex", "showwavespic=s=640x360", "-frames:v", "1", "-c:v", "libwebp", "-q:v", "80", th),
                java.time.Duration.ofSeconds(120)
            ) && fileSize(th) > 0
        ) thumb = thname
        synchronized(Db.lock) {
            Db.conn.prepareStatement("UPDATE videos SET filename=?, size=?, thumbnail=?, mimetype=?, orientation=?, status='ready' WHERE id=?").use { ps ->
                ps.setString(1, "$stem.ogg")
                ps.setLong(2, size)
                if (thumb == null) ps.setNull(3, java.sql.Types.VARCHAR) else ps.setString(3, thumb)
                ps.setString(4, "audio/ogg")
                ps.setString(5, "h")
                ps.setLong(6, id)
                ps.executeUpdate()
            }
        }
        notifyFollowers(id, author)
    }

    fun backupOnce() {
        val dir = File("${Config.dataDir}/backups")
        dir.mkdirs()
        val dst = "${dir.absolutePath}/backup-${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.sqlite"
        synchronized(Db.lock) {
            try {
                Db.conn.createStatement().use { it.execute("VACUUM INTO '${dst.replace("'", "''")}'") }
            } catch (_: Exception) {
                return
            }
        }
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
            val tmp = File("${Config.videosDir}/${r.fn}")
            if (!tmp.exists() || tmp.length() == 0L) {
                markFailed(r.id)
                continue
            }
            val stem = r.fn.removeSuffix(".part")
            if (r.kind == "music") bg.submit { processMusic(r.id, r.author, tmp.absolutePath, stem, "") }
            else bg.submit { processUpload(r.id, r.author, tmp.absolutePath, stem, "") }
        }
        val entries = File(Config.videosDir).listFiles() ?: return
        for (e in entries) {
            if (!e.name.endsWith(".part")) continue
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
            val entries = File(dir).listFiles() ?: continue
            for (e in entries) {
                val nm = e.name
                if (nm.startsWith(".")) continue
                if (nm.endsWith(".part")) continue
                if (!e.isFile) continue
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
            val (w, h, ok) = probeDims("${Config.videosDir}/${r.fn}")
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
