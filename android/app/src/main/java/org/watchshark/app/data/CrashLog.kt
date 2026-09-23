package org.watchshark.app.data

import android.content.Context
import android.util.Log

/** Captures uncaught crashes to a file so users can send the exact stack trace. */
object CrashLog {
    private const val PREFS = "watchshark_crash"
    private const val KEY_CRASH = "last_crash"
    private const val TAG = "WatchShark"

    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildString {
                    append("Version: ")
                    append(Updater.currentVersion(appCtx))
                    append("\nThread: ")
                    append(thread.name)
                    append("\n\n")
                    append(Log.getStackTraceString(throwable))
                }.take(8000)
                appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH, report)
                    .apply()
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(ctx: Context): String? =
        ctx.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CRASH, null)

    fun clear(ctx: Context) {
        ctx.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CRASH)
            .apply()
    }

    /** Shows the saved crash report with Copy / Clear actions. No-op if none. */
    fun showNow(ctx: Context) {
        val report = lastCrash(ctx) ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("Last crash report")
            .setMessage(report)
            .setNegativeButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> clear(ctx) }
            .setPositiveButton("Copy") { _, _ ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                cm?.setPrimaryClip(
                    android.content.ClipData.newPlainText("crash", report)
                )
                android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
