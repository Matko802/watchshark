package watchshark.duckdns.org.data

import android.content.Context

object NavPrefs {
    private const val PREFS = "watchshark_nav"
    private const val KEY_LABELS = "bottom_labels"

    fun showLabels(ctx: Context): Boolean {
        return try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LABELS, true)
        } catch (_: Exception) {
            true
        }
    }

    fun setShowLabels(ctx: Context, show: Boolean) {
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_LABELS, show).apply()
        } catch (_: Exception) {
        }
    }
}
