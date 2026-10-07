package watchshark.duckdns.org.data

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import watchshark.duckdns.org.MainActivity
import watchshark.duckdns.org.R

object UploadAlerts {
    const val CHANNEL_UPLOADS = "uploads"
    const val ACTION_WATCH = "watchshark.duckdns.org.action.WATCH"
    private const val WORK = "upload-check"
    private const val PREFS = "watchshark_uploads"
    private const val KEY_LAST_ID = "last_notif_id"
    private const val KEY_ASKED = "asked_perm"
    private const val MAX_PER_RUN = 5
    private const val PERM_CODE = 4001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_UPLOADS) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_UPLOADS, "Uploads", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun schedule(ctx: Context) {
        ensureChannel(ctx)
        val req = PeriodicWorkRequestBuilder<UploadCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(WORK)
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK)
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureScheduled(activity: Activity) {
        if (ApiClient.sessionToken().isNullOrEmpty()) return
        schedule(activity)
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(activity)) {
            val p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!p.getBoolean(KEY_ASKED, false)) {
                p.edit().putBoolean(KEY_ASKED, true).apply()
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERM_CODE
                )
            }
        }
    }

    class UploadCheckWorker(appCtx: Context, params: WorkerParameters) :
        CoroutineWorker(appCtx, params) {
        override suspend fun doWork(): Result {
            return try {
                ApiClient.init(applicationContext)
                if (ApiClient.sessionToken().isNullOrEmpty()) return Result.success()
                val items = ApiClient.api.notifications().notifications.orEmpty()
                    .filter { !it.read && it.kind != "delete" && (it.videoId ?: 0) > 0 }
                val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (items.isNotEmpty()) {
                    val last = prefs.getLong(KEY_LAST_ID, 0)
                    val maxId = items.maxOf { it.id }
                    if (last == 0L) {
                        prefs.edit().putLong(KEY_LAST_ID, maxId).apply()
                    } else {
                        val fresh = items.filter { it.id > last }.sortedBy { it.id }.takeLast(MAX_PER_RUN)
                        prefs.edit().putLong(KEY_LAST_ID, maxId).apply()
                        if (fresh.isNotEmpty() && hasPermission(applicationContext)) {
                            ensureChannel(applicationContext)
                            for (n in fresh) notifyUpload(n)
                        }
                    }
                }
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }

        private fun notifyUpload(n: Notif) {
            val ctx = applicationContext
            val intent = Intent(ctx, MainActivity::class.java).apply {
                action = ACTION_WATCH
                putExtra("video_id", n.videoId ?: 0)
                putExtra("notif_id", n.id)
            }
            val pi = PendingIntent.getActivity(
                ctx,
                n.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val built = NotificationCompat.Builder(ctx, CHANNEL_UPLOADS)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("@${n.username} uploaded")
                .setContentText(n.title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setGroup("uploads")
                .build()
            NotificationManagerCompat.from(ctx).notify(n.id.toInt(), built)
        }
    }
}
