package watchshark.duckdns.org.data

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PlayerPrefs {
    const val SCRUB_SLIM = 0
    const val SCRUB_CLASSIC = 1
    const val SCRUB_BOLD = 2

    private const val PREFS = "watchshark_player"
    private const val KEY_SCRUB = "scrub_style"

    fun scrubStyle(ctx: Context): Int {
        return try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_SCRUB, SCRUB_SLIM).coerceIn(SCRUB_SLIM, SCRUB_BOLD)
        } catch (_: Exception) {
            SCRUB_SLIM
        }
    }

    fun setScrubStyle(ctx: Context, style: Int) {
        try {
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_SCRUB, style.coerceIn(SCRUB_SLIM, SCRUB_BOLD)).apply()
        } catch (_: Exception) {
        }
    }

    fun scrubBaseHeight(style: Int): Dp = when (style) {
        SCRUB_BOLD -> 6.dp
        SCRUB_CLASSIC -> 4.dp
        else -> 2.dp
    }
}
