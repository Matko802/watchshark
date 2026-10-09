package watchshark.duckdns.org.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Button vibration feedback (SpatialFlow-style): master toggle plus
 * 0–100 strength slider with a test buzz on change.
 */
object Haptics {
    private const val PREFS = "watchshark_haptics"
    private const val KEY_ON = "haptics_on"
    private const val KEY_STRENGTH = "haptics_strength"

    fun isOn(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ON, true)
    }

    fun setOn(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ON, on).apply()
        if (on) buzz(ctx, 30)
    }

    fun strength(ctx: Context): Float {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_STRENGTH, 80f)
            .coerceIn(0f, 100f)
    }

    fun setStrength(ctx: Context, value: Float) {
        val s = value.coerceIn(0f, 100f)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_STRENGTH, s).apply()
        if (s > 0) buzz(ctx, 40)
    }

    private fun vibrator(ctx: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buzz(ctx: Context, millis: Long) {
        try {
            val vib = vibrator(ctx) ?: return
            if (!vib.hasVibrator()) return
            val amplitude = (strength(ctx) / 100f * 255).toInt().coerceIn(1, 255)
            vib.vibrate(VibrationEffect.createOneShot(millis, amplitude))
        } catch (_: Exception) {
        }
    }

    /** Short tick for button taps. Silent when toggled off. */
    fun tick(ctx: Context) {
        if (!isOn(ctx)) return
        buzz(ctx, 20)
    }
}
