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
    /** Update selected by the user, consumed by the fullscreen update screen. */
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
    /** Must be called once at startup (alongside ApiClient.init). */
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
    /** Installed app version, e.g. 1.6.8. */
    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            ?: BuildConfig.VERSION_NAME
    } catch (_: Exception) {
        BuildConfig.VERSION_NAME
    }
    /**
     * Returns Available / UpToDate / Failed (network, HTTP error).
     * Uses the public releases page (no API rate limits) and a
     * deterministic asset URL — no api.github.com involved.
     * Never throws; callers must surface Failed instead of pretending
     * everything is up to date.
     */
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
    /**
     * Debug builds and release builds are signed with different keys, so a
     * release APK can never install over a debug one (and vice versa).
     * Detect it up front instead of dumping the user at a dead installer.
     */
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

    /**
     * Downloads [update] on IO, reporting (receivedBytes, totalBytes).
     * Throws on HTTP errors or incomplete downloads.
     */
    suspend fun downloadApk(
        ctx: Context,
        update: AppUpdate,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(update.url).get().build()
        client().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val total = resp.body?.contentLength() ?: update.size
            val file = File(ctx.cacheDir, "watchshark-update.apk")
            if (file.exists()) file.delete()
            var received = 0L
            resp.body!!.byteStream().use { input ->
                file.outputStream().use { out ->
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
            file
        }
    }

    /**
     * Verifies + installs a downloaded APK. Returns an error message, or
     * null when the installer was launched.
     */
    fun installDownloaded(ctx: Context, file: File, update: AppUpdate): String? {
        if (!signaturesMatch(ctx, file)) {
            return "This update is signed with a different key than the installed app, " +
                "so Android refuses to install it. Uninstall WatchShark first, then install " +
                "the downloaded update — your account and videos stay on the server, just log in again."
        }
        installApk(ctx, file)
        return null
    }
}
