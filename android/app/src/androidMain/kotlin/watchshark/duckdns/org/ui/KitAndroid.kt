package watchshark.duckdns.org.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import java.util.TimeZone
import watchshark.duckdns.org.data.ThemePrefs

actual typealias Context = android.content.Context

actual object Uikit {
    internal var appCtx: Context? = null

    fun attach(c: android.content.Context) {
        appCtx = c.applicationContext
    }

    actual val ctx: Context
        get() = appCtx ?: error("Uikit not attached")

    actual val baseUrl: String
        get() = try {
            watchshark.duckdns.org.BuildConfig.APP_URL
        } catch (_: Exception) {
            "https://watchshark.duckdns.org"
        }

    actual fun nowSec(): Long = System.currentTimeMillis() / 1000

    actual fun monoMs(): Long = SystemClock.elapsedRealtime()

    actual fun localOffsetSec(epochSec: Long): Int =
        TimeZone.getDefault().getOffset(epochSec * 1000L) / 1000

    actual fun toast(msg: String) {
        try {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    actual fun vibrate(ms: Long) {
        try {
            val vib = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            } ?: return
            if (!vib.hasVibrator()) return
            val strength = Uikit.prefGet("haptics_strength")?.toFloatOrNull() ?: 80f
            val amplitude = ((strength / 100f) * 255).toInt().coerceIn(1, 255)
            vib.vibrate(android.os.VibrationEffect.createOneShot(ms.coerceIn(0L, 5000L), amplitude))
        } catch (_: Exception) {
        }
    }

    actual fun openUrl(url: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    actual fun shareText(text: String, title: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(Intent.createChooser(send, title))
        } catch (_: Exception) {
        }
    }

    actual fun setSessionToken(token: String?) {
        try {
            val p = ctx.getSharedPreferences("watchshark_session", Context.MODE_PRIVATE)
            if (token == null) p.edit().remove("ws_token").apply()
            else p.edit().putString("ws_token", token).apply()
        } catch (_: Exception) {
        }
    }

    actual fun getSessionToken(): String? {
        return try {
            ctx.getSharedPreferences("watchshark_session", Context.MODE_PRIVATE)
                .getString("ws_token", null)
        } catch (_: Exception) {
            null
        }
    }

    actual fun prefGet(key: String): String? {
        return try {
            ctx.getSharedPreferences("watchshark_kmp", Context.MODE_PRIVATE)
                .getString(key, null)
        } catch (_: Exception) {
            null
        }
    }

    actual fun prefSet(key: String, value: String?) {
        try {
            val p = ctx.getSharedPreferences("watchshark_kmp", Context.MODE_PRIVATE)
            if (value == null) p.edit().remove(key).apply()
            else p.edit().putString(key, value).apply()
        } catch (_: Exception) {
        }
    }

    actual fun copyToClipboard(text: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("WatchShark", text))
        } catch (_: Exception) {
        }
    }

    actual fun applyThemeMode(mode: Int) {
        val night = when (mode) {
            ThemePrefs.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePrefs.MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(night)
    }

    actual fun appIcon(): Any? {
        return try {
            ctx.applicationInfo.loadIcon(ctx.packageManager)
        } catch (_: Exception) {
            null
        }
    }

    actual fun hasDynamicColor(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
}

@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
