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
import org.json.JSONArray
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
    private const val RELEASES_URL =
        "https://api.github.com/repos/Matko802/watchshark/releases?per_page=30"

    /** Silent auto-check at most once per this interval (manual taps bypass it). */
    private const val CHECK_THROTTLE_MS = 24 * 60 * 60 * 1000L
    private const val PREFS = "watchshark_update"
    private const val KEY_LAST_CHECK = "last_check"

    @Volatile
    private var http: OkHttpClient? = null
    @Volatile
    private var appContext: Context? = null
    private val checking = java.util.concurrent.atomic.AtomicBoolean(false)

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
     * Returns Available / UpToDate / Failed (network, HTTP error, rate limit).
     * Never throws; callers must surface Failed instead of pretending
     * everything is up to date.
     */
    suspend fun checkForUpdate(): UpdateCheck = withContext(Dispatchers.IO) {
        val current = parseVer(BuildConfig.VERSION_NAME)
        try {
            val req = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client().newCall(req).execute().use { resp ->
                if (resp.code == 403) {
                    val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()
                    val when_ = if (reset != null) {
                        val mins = ((reset * 1000 - System.currentTimeMillis()) / 60000)
                            .coerceAtLeast(1)
                        " (retry in ~$mins min)"
                    } else ""
                    return@withContext UpdateCheck.Failed("GitHub rate limit$when_")
                }
                if (!resp.isSuccessful) {
                    return@withContext UpdateCheck.Failed("Check failed (HTTP ${resp.code})")
                }
                val body = resp.body?.string() ?: return@withContext UpdateCheck.Failed(
                    "Check failed (empty response)"
                )
                val releases = JSONArray(body)
                var best: AppUpdate? = null
                for (i in 0 until releases.length()) {
                    val r = releases.optJSONObject(i) ?: continue
                    val tag = r.optString("tag_name", "")
                    if (!tag.startsWith("android-v")) continue
                    if (cmpVer(parseVer(tag), current) <= 0) continue
                    val assets = r.optJSONArray("assets") ?: continue
                    var apkUrl = ""
                    var apkSize = 0L
                    for (j in 0 until assets.length()) {
                        val a = assets.optJSONObject(j) ?: continue
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", "")
                            apkSize = a.optLong("size", 0)
                            break
                        }
                    }
                    if (apkUrl.isEmpty()) continue
                    val cand = AppUpdate(
                        tag.removePrefix("android-v"),
                        r.optString("body", ""),
                        apkUrl, apkSize
                    )
                    if (best == null || cmpVer(parseVer(cand.version), parseVer(best.version)) > 0) {
                        best = cand
                    }
                }
                best?.let { UpdateCheck.Available(it) } ?: UpdateCheck.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheck.Failed("Could not check for updates (${e.message ?: "network error"})")
        }
    }

    /** Silent check (e.g. on launch): only shows a dialog when an update exists. */
    fun checkSilent(host: Fragment) {
        if (!checking.compareAndSet(false, true)) return
        if (checkedRecently()) {
            checking.set(false)
            return
        }
        host.lifecycleScope.launch {
            try {
                val result = checkForUpdate()
                stampCheck()
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
        // firing a new API call per tap (GitHub allows 60/hr unauthenticated).
        if (!checking.compareAndSet(false, true)) {
            onStatus("Already checking…")
            return
        }
        host.lifecycleScope.launch {
            try {
                when (val result = checkForUpdate()) {
                    is UpdateCheck.Available -> {
                        if (host.isAdded) promptUpdate(host, result.update)
                    }
                    UpdateCheck.UpToDate -> {
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

    private fun checkedRecently(): Boolean {
        val last = prefs()?.getLong(KEY_LAST_CHECK, 0) ?: 0
        return System.currentTimeMillis() - last < CHECK_THROTTLE_MS
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

    private fun installApk(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(
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
