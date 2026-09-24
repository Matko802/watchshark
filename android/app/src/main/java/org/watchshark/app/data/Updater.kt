package org.watchshark.app.data

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.watchshark.app.BuildConfig
import org.watchshark.app.R
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
    // NOTE: intentionally NOT api.github.com — its 60 req/hour IP limit
    // breaks update checks on shared mobile networks. The web endpoints
    // below have no such limit.
    private const val LATEST_URL =
        "https://github.com/Matko802/watchshark/releases/latest"
    private const val TAG_URL_PREFIX =
        "https://github.com/Matko802/watchshark/releases/tag/"

    /** Silent auto-check at most once per this interval. */
    private const val SILENT_COOLDOWN_MS = 24 * 60 * 60 * 1000L
    /** Manual taps share a shorter cooldown so spamming can't hammer GitHub. */
    private const val MANUAL_COOLDOWN_MS = 60 * 60 * 1000L
    private const val PREFS = "watchshark_update"
    private const val KEY_LAST_CHECK = "last_check"

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
                    // HTTP cache: GitHub answers conditional requests with 304,
                    // which does NOT consume rate limit.
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
            // /releases/latest 302-redirects to /releases/tag/<tag>.
            var tag: String? = null
            Request.Builder().url(LATEST_URL).get().build().let { req ->
                noRedirectHttp.newCall(req).execute().use { resp ->
                    val loc = resp.header("Location", "").orEmpty()
                    if ((resp.code == 301 || resp.code == 302) && loc.startsWith(TAG_URL_PREFIX)) {
                        tag = loc.removePrefix(TAG_URL_PREFIX).substringBefore('/').substringBefore('?')
                    } else if (resp.isSuccessful) {
                        // Already at latest page without redirect (unexpected);
                        // fall back to parsing below via API-free atom feed is
                        // overkill — treat as up to date only if nothing newer.
                        return@withContext UpdateCheck.UpToDate
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

    /** Silent check (e.g. on launch): only shows a dialog when an update exists. */
    fun checkSilent(host: Fragment) {
        if (!checking.compareAndSet(false, true)) return
        if (cooledDown(SILENT_COOLDOWN_MS)) {
            checking.set(false)
            return
        }
        host.lifecycleScope.launch {
            try {
                val result = checkForUpdate()
                if (result is UpdateCheck.Available || result is UpdateCheck.UpToDate) {
                    stampCheck()
                }
                if (result is UpdateCheck.Available && host.isAdded) {
                    promptUpdate(host, result.update)
                }
            } finally {
                checking.set(false)
            }
        }
    }

    /** Manual check with feedback (status message when up to date or on error). */
    fun checkManual(host: Fragment, onStatus: (String) -> Unit) {
        // Spam-tapping the button reuses the in-flight check instead of
        // firing a new call per tap, plus a cooldown between checks.
        if (!checking.compareAndSet(false, true)) {
            onStatus("Already checking…")
            return
        }
        if (cooledDown(MANUAL_COOLDOWN_MS)) {
            checking.set(false)
            onStatus("Already checked — up to date")
            return
        }
        host.lifecycleScope.launch {
            try {
                when (val result = checkForUpdate()) {
                    is UpdateCheck.Available -> {
                        stampCheck()
                        if (host.isAdded) promptUpdate(host, result.update)
                    }
                    UpdateCheck.UpToDate -> {
                        stampCheck()
                        if (host.isAdded) onStatus("Already on the latest version")
                    }
                    is UpdateCheck.Failed -> {
                        if (host.isAdded) onStatus(result.reason)
                    }
                }
            } finally {
                checking.set(false)
            }
        }
    }

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True if a successful check happened within [windowMs] (failures don't count). */
    private fun cooledDown(windowMs: Long): Boolean {
        val last = prefs()?.getLong(KEY_LAST_CHECK, 0) ?: 0
        return System.currentTimeMillis() - last < windowMs
    }

    private fun stampCheck() {
        prefs()?.edit()?.putLong(KEY_LAST_CHECK, System.currentTimeMillis())?.apply()
    }

    private fun promptUpdate(host: Fragment, update: AppUpdate) {
        val ctx = host.requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Update available (${update.version})")
            .setMessage("${update.notes}\n\nSize: ${fmtSize(update.size)}".trim())
            .setNegativeButton("Later", null)
            .setPositiveButton("Update") { _, _ -> downloadAndInstall(host, update) }
            .show()
    }

    private fun downloadAndInstall(host: Fragment, update: AppUpdate) {
        val ctx = host.requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_download, null)
        val bar: ProgressBar = view.findViewById(R.id.dl_bar)
        val label: TextView = view.findViewById(R.id.dl_label)
        label.text = "Starting…"
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Downloading update")
            .setView(view)
            .setCancelable(false)
            .create()
        var job: kotlinx.coroutines.Job? = null
        dialog.setButton(
            android.content.DialogInterface.BUTTON_NEGATIVE, "Cancel"
        ) { _, _ ->
            job?.cancel()
            dialog.dismiss()
        }
        dialog.show()

        job = host.lifecycleScope.launch(Dispatchers.IO) {
            try {
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
                                if (total > 0) {
                                    val pct = (received * 100 / total).toInt()
                                    withContext(Dispatchers.Main) {
                                        bar.isIndeterminate = false
                                        bar.progress = pct
                                        label.text =
                                            "$pct% • ${fmtSize(received)} / ${fmtSize(total)}"
                                    }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        if (total > 0 && file.length() != total) {
                            showError(host, "Download incomplete, try again")
                            return@withContext
                        }
                        if (!signaturesMatch(ctx, file)) {
                            showSignatureMismatch(host)
                            return@withContext
                        }
                        installApk(ctx, file)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    bar.isIndeterminate = false
                    label.text = "Download failed: ${e.message ?: "network error"}"
                }
            }
        }
    }

    private fun showError(host: Fragment, message: String) {
        if (!host.isAdded) return
        MaterialAlertDialogBuilder(host.requireContext())
            .setTitle("Update failed")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
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

    private fun showSignatureMismatch(host: Fragment) {
        if (!host.isAdded) return
        val ctx = host.requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Can't install over this version")
            .setMessage(
                "This update is signed with a different key than the installed app, " +
                    "so Android refuses to install it. Uninstall WatchShark first, then install " +
                    "the downloaded update — your account and videos stay on the server, just log in again."
            )
            .setNegativeButton("Close", null)
            .setPositiveButton("Uninstall app") { _, _ ->
                try {
                    ctx.startActivity(
                        Intent(
                            Intent.ACTION_DELETE,
                            android.net.Uri.parse("package:${ctx.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                } catch (_: Exception) {
                }
            }
            .show()
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
}
