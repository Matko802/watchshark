package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context
import watchshark.duckdns.org.ui.Uikit

object NavPrefs {
    fun showLabels(ctx: Context): Boolean {
        return try {
            Uikit.prefGet("bottom_labels") != "false"
        } catch (_: Exception) {
            true
        }
    }

    fun setShowLabels(ctx: Context, show: Boolean) {
        try {
            Uikit.prefSet("bottom_labels", if (show) "true" else "false")
        } catch (_: Exception) {
        }
    }
}
