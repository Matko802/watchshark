package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context
import watchshark.duckdns.org.ui.Uikit

object ThemePrefs {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
    const val MODE_AMOLED = 3

    fun getMode(ctx: Context): Int {
        return Uikit.prefGet("theme_mode")?.toIntOrNull()?.coerceIn(MODE_SYSTEM, MODE_AMOLED)
            ?: MODE_SYSTEM
    }

    fun setMode(ctx: Context, mode: Int) {
        val m = mode.coerceIn(MODE_SYSTEM, MODE_AMOLED)
        Uikit.prefSet("theme_mode", m.toString())
        Uikit.applyThemeMode(m)
    }

    fun toDarkOverride(mode: Int): Boolean? = when (mode) {
        MODE_LIGHT -> false
        MODE_DARK, MODE_AMOLED -> true
        else -> null
    }
}
