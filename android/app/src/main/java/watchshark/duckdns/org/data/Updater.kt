package watchshark.duckdns.org.data
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import watchshark.duckdns.org.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
data class AppUpdate(
    val version: String,
    val notes: String,
    val url: String,
    val size: Long
)
sealed interface UpdateCheck {
    data class Available(val update: AppUpdate) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}
object Updater {

    @Volatile
    var pending: AppUpdate? = null
    private const val LATEST_URL =
        "https://github.com/Matko802/watchshark/releases/latest"
    private const val TAG_URL_PREFIX =
        "https://github.com/Matko802/watchshark/releases/tag/"

    @Volatile
    private var http: OkHttpClient? = null
    @Volatile
    private var appContext: Context? = null
    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)
    private val noRedirectHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        if (http == null) {
            synchronized(this) {
                if (http == null) {
                    val cache = try {
                        okhttp3.Cache(File(ctx.cacheDir, "gh_api"), 1L * 1024 * 1024)
                    } catch (_: Exception) {
                        null
                    }
                    val builder = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                    if (cache != null) builder.cache(cache)
                    http = builder.build()
                }
            }
        }
    }
    private fun client(): OkHttpClient {
        appContext?.let { init(it) }
        return http!!
    }
    private fun parseVer(v: String): List<Int> {
        val m = Regex("""(\d+)\.(\d+)\.(\d+)""").find(v) ?: return listOf(0, 0, 0)
        return (1..3).map { m.groupValues[it].toInt() }
    }
    private fun cmpVer(a: List<Int>, b: List<Int>): Int {
        for (i in 0..2) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return 0
    }
    fun fmtSize(n: Long): String {
        if (n <= 0) return ""
        return if (n >= 1048576) "${n / 1048576} MB" else "${n / 1024} KB"
    }

    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            ?: BuildConfig.VERSION_NAME
    } catch (_: Exception) {
        BuildConfig.VERSION_NAME
    }


    suspend fun checkForUpdate(): UpdateCheck = withContext(Dispatchers.IO) {
        val current = parseVer(BuildConfig.VERSION_NAME)
        try {
            var tag: String? = null
            Request.Builder().url(LATEST_URL).get().build().let { req ->
                noRedirectHttp.newCall(req).execute().use { resp ->
                    val loc = resp.header("Location", "").orEmpty()
                    if ((resp.code == 301 || resp.code == 302) && loc.startsWith(TAG_URL_PREFIX)) {
                        tag = loc.removePrefix(TAG_URL_PREFIX).substringBefore('/').substringBefore('?')
                    } else if (resp.isSuccessful) {
                        return@withContext UpdateCheck.Failed("Check failed (unexpected response)")
                    } else {
                        return@withContext UpdateCheck.Failed("Check failed (HTTP ${resp.code})")
                    }
                }
            }
            val t = tag?.takeIf { it.startsWith("android-v") }
                ?: return@withContext UpdateCheck.Failed("Check failed (bad response)")
            if (cmpVer(parseVer(t), current) <= 0) return@withContext UpdateCheck.UpToDate
            val version = t.removePrefix("android-v")
            val apkUrl = "https://github.com/Matko802/watchshark/releases/download/$t/WatchShark-$version.apk"
            var size = 0L
            try {
                Request.Builder().url(apkUrl).head().build().let { req ->
                    client().newCall(req).execute().use { resp ->
                        size = resp.header("Content-Length", "0")?.toLongOrNull() ?: 0L
                    }
                }
            } catch (_: Exception) {
            }
            UpdateCheck.Available(AppUpdate(version, "", apkUrl, size))
        } catch (e: Exception) {
            UpdateCheck.Failed("Could not check for updates (${e.message ?: "network error"})")
        }
    }


    private fun signaturesMatch(ctx: Context, apkFile: File): Boolean {
        return try {
            val pm = ctx.packageManager
            val installedSigs: Set<String> = if (android.os.Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                    .signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            }
            val archiveSigs: Set<String> = if (android.os.Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageArchiveInfo(apkFile.absolutePath, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                info?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
                    ?.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            }
            if (installedSigs.isEmpty() || archiveSigs.isEmpty()) return true
            installedSigs.intersect(archiveSigs).isNotEmpty()
        } catch (_: Exception) {
            true
        }
    }
    private fun installApk(ctx: Context, file: File) {        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }


    suspend fun downloadApk(
        ctx: Context,
        update: AppUpdate,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        pruneOldDownloads(ctx, update.version)
        val file = File(ctx.cacheDir, "watchshark-update-${update.version}.apk")
        val expected = update.size.takeIf { it > 0 }
        if (expected != null && file.exists() && file.length() == expected) {
            onProgress(expected, expected)
            return@withContext file
        }
        var attempt = 0
        while (true) {
            attempt++
            val resumeFrom =
                if (attempt == 1 && expected != null && file.exists() && file.length() in 1 until expected) {
                    file.length()
                } else {
                    0L
                }
            if (resumeFrom == 0L && file.exists()) file.delete()
            val req = Request.Builder().url(update.url).get().apply {
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }.build()
            client().newCall(req).execute().use { resp ->
                val resumeRefused = resumeFrom > 0 && resp.code != 206
                if (resumeRefused) {
                    file.delete()
                    if (attempt >= 2) throw IllegalStateException("Server refused resume, try again")
                } else {
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val bodyLen = resp.body?.contentLength() ?: -1L
                    val total = if (bodyLen < 0) expected ?: -1L else bodyLen + resumeFrom
                    var received = resumeFrom
                    onProgress(received, total)
                    resp.body!!.byteStream().use { input ->
                        FileOutputStream(file, resumeFrom > 0).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                received += n
                                onProgress(received, total)
                            }
                        }
                    }
                    if (total > 0 && file.length() != total) {
                        throw IllegalStateException("Download incomplete, try again")
                    }
                    return@withContext file
                }
            }
        }
        throw IllegalStateException("unreachable")
    }

    private fun pruneOldDownloads(ctx: Context, keepVersion: String) {
        pruneApkCache(ctx, keepVersion)
    }

    fun cachedApkFile(ctx: Context, version: String): File {
        return File(ctx.cacheDir, "watchshark-update-$version.apk")
    }

    fun pruneApkCache(ctx: Context, keepVersion: String) {
        try {
            ctx.cacheDir.listFiles { f ->
                f.isFile && f.name.startsWith("watchshark-update") &&
                    f.name != "watchshark-update-$keepVersion.apk"
            }?.forEach {
                try {
                    it.delete()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
    fun installDownloaded(ctx: Context, file: File, update: AppUpdate): String? {
        if (!signaturesMatch(ctx, file)) {
            return "This 1.9.0 update uses a new signing key, so Android cannot install it over the old app. Uninstall WatchShark first, then install the downloaded update. Your account and videos stay on the server, just log in again."
        }
        installApk(ctx, file)
        return null
    }
}
