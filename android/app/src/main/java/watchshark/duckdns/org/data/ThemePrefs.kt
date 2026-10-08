package watchshark.duckdns.org.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Persisted appearance choice (Settings → App → Appearance). */
object ThemePrefs {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
    const val MODE_AMOLED = 3
    const val MODE_GREY = 4

    private const val PREFS = "watchshark_theme"
    private const val KEY_MODE = "theme_mode"

    fun getMode(ctx: Context): Int {
        return when (val stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, MODE_SYSTEM)) {
            // Legacy Light/Dark options were replaced by System / Grey /
            // AMOLED — migrate old installs to System default.
            MODE_LIGHT, MODE_DARK -> MODE_SYSTEM
            else -> stored.coerceIn(MODE_SYSTEM, MODE_GREY)
        }
    }

    fun setMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode.coerceIn(MODE_SYSTEM, MODE_GREY))
            .apply()
    }

    /** Views side (AppCompat): AMOLED and Grey render as night. */
    fun toNightMode(mode: Int): Int = when (mode) {
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    /** Compose side: null = follow system. */
    fun toDarkOverride(mode: Int): Boolean? = when (mode) {
        MODE_LIGHT -> false
        MODE_DARK, MODE_AMOLED, MODE_GREY -> true
        else -> null
    }
}
