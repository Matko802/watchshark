package watchshark.duckdns.org.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApkDownload {
    const val WORK_PREFIX = "apk-update-"
    const val KEY_URL = "url"
    const val KEY_VERSION = "version"
    const val KEY_EXPECTED = "expected"
    const val KEY_PROGRESS = "progress"
    const val NOTIF_ID = 9001
    const val CHANNEL = "updates"

    fun workName(version: String) = WORK_PREFIX + version

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL) != null) return
            mgr.createNotificationChannel(
                android.app.NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_LOW)
            )
        } catch (_: Exception) {
        }
    }

    fun enqueue(ctx: Context, update: AppUpdate) {
        ensureChannel(ctx.applicationContext)
        val req = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(
                workDataOf(
                    KEY_URL to update.url,
                    KEY_VERSION to update.version,
                    KEY_EXPECTED to update.size,
                )
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniqueWork(workName(update.version), ExistingWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context, version: String) {
        try {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(workName(version))
        } catch (_: Exception) {
        }
    }

    class ApkDownloadWorker(appCtx: Context, params: WorkerParameters) :
        CoroutineWorker(appCtx, params) {

        override suspend fun doWork(): Result {
            val url = inputData.getString(KEY_URL) ?: return Result.failure()
            val version = inputData.getString(KEY_VERSION) ?: return Result.failure()
            val expected = inputData.getLong(KEY_EXPECTED, 0)
            val ctx = applicationContext
            ensureChannel(ctx)
            Updater.pruneApkCache(ctx, version)
            val file = Updater.cachedApkFile(ctx, version)
            if (expected > 0 && file.exists() && file.length() == expected) {
                setProgress(workDataOf(KEY_PROGRESS to 100))
                notifyProgress(ctx, version, 100)
                notifyDone(ctx, version)
                return Result.success()
            }
            var resumeFrom =
                if (expected > 0 && file.exists() && file.length() in 1 until expected) file.length()
                else 0L
            if (resumeFrom == 0L && file.exists()) file.delete()
            try {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val req = Request.Builder().url(url).get().apply {
                    if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (resumeFrom > 0 && resp.code != 206) {
                        file.delete()
                        resumeFrom = 0
                        return Result.retry()
                    }
                    if (!resp.isSuccessful) {
                        return if (resp.code in 500..599) Result.retry() else Result.failure()
                    }
                    val bodyLen = resp.body?.contentLength() ?: -1L
                    val total = if (bodyLen < 0) {
                        if (expected > 0) expected else -1L
                    } else {
                        bodyLen + resumeFrom
                    }
                    var received = resumeFrom
                    var lastPct = -1
                    resp.body!!.byteStream().use { input ->
                        FileOutputStream(file, resumeFrom > 0).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (isStopped) return Result.retry()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                received += n
                                if (total > 0) {
                                    val pct = ((received * 100) / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct && (pct - lastPct >= 2 || pct == 100)) {
                                        lastPct = pct
                                        setProgress(workDataOf(KEY_PROGRESS to pct))
                                        notifyProgress(ctx, version, pct)
                                    }
                                }
                            }
                        }
                    }
                    if (total > 0 && file.length() != total) return Result.retry()
                    setProgress(workDataOf(KEY_PROGRESS to 100))
                    notifyDone(ctx, version)
                    return Result.success()
                }
            } catch (e: Exception) {
                if (e is IOException) return Result.retry()
                return Result.failure()
            }
        }

        private fun notifyProgress(ctx: Context, version: String, pct: Int) {
            if (!UploadAlerts.hasPermission(ctx)) return
            try {
                val built = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle("Downloading update v$version")
                    .setContentText("$pct%")
                    .setProgress(100, pct, false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID, built)
            } catch (_: Exception) {
            }
        }

        private fun notifyDone(ctx: Context, version: String) {
            if (!UploadAlerts.hasPermission(ctx)) return
            try {
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val pi = PendingIntent.getActivity(
                    ctx,
                    NOTIF_ID,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val built = NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle("Update downloaded")
                    .setContentText("Tap to install v$version")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
                NotificationManagerCompat.from(ctx).notify(NOTIF_ID, built)
            } catch (_: Exception) {
            }
        }
    }
}
