package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context
import watchshark.duckdns.org.ui.Uikit

object Haptics {
    fun isOn(ctx: Context): Boolean {
        return try {
            Uikit.prefGet("haptics_on") != "false"
        } catch (_: Exception) {
            true
        }
    }

    fun setOn(ctx: Context, on: Boolean) {
        try {
            Uikit.prefSet("haptics_on", if (on) "true" else "false")
        } catch (_: Exception) {
        }
        if (on) Uikit.vibrate(30)
    }

    fun strength(ctx: Context): Float {
        return try {
            (Uikit.prefGet("haptics_strength")?.toFloatOrNull() ?: 80f).coerceIn(0f, 100f)
        } catch (_: Exception) {
            80f
        }
    }

    fun setStrength(ctx: Context, value: Float) {
        val s = value.coerceIn(0f, 100f)
        try {
            Uikit.prefSet("haptics_strength", s.toString())
        } catch (_: Exception) {
        }
        if (s > 0) Uikit.vibrate(40)
    }

    fun tick(ctx: Context) {
        try {
            if (!isOn(ctx)) return
            Uikit.vibrate(20)
        } catch (_: Exception) {
        }
    }
}
