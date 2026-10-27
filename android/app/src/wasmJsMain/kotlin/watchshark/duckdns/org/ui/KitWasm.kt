package watchshark.duckdns.org.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.window
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Instant

actual abstract class Context

internal class WebContext : Context()

actual object Uikit {
    private val ctxInstance = WebContext()

    actual val ctx: Context
        get() = ctxInstance

    actual val baseUrl: String
        get() = try {
            window.location.origin.ifEmpty { "https://watchshark.duckdns.org" }
        } catch (_: Exception) {
            "https://watchshark.duckdns.org"
        }

    actual fun nowSec(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    actual fun monoMs(): Long = window.performance.now().toLong()

    actual fun localOffsetSec(epochSec: Long): Int =
        TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochSeconds(epochSec)).totalSeconds

    actual fun toast(msg: String) {
        try {
            println(msg)
        } catch (_: Exception) {
        }
    }

    actual fun vibrate(ms: Long) {
        try {
            window.navigator.vibrate(ms.toInt())
        } catch (_: Exception) {
        }
    }

    actual fun openUrl(url: String) {
        try {
            window.location.href = url
        } catch (_: Exception) {
        }
    }

    actual fun shareText(text: String, title: String) {
        copyToClipboard(text)
    }

    actual fun setSessionToken(token: String?) {
        try {
            val ls = window.localStorage
            if (token == null) {
                ls.removeItem("ws_token")
            } else {
                ls.setItem("ws_token", token)
            }
        } catch (_: Exception) {
        }
    }

    actual fun getSessionToken(): String? =
        try {
            window.localStorage.getItem("ws_token")
        } catch (_: Exception) {
            null
        }

    actual fun prefGet(key: String): String? =
        try {
            window.localStorage.getItem(key)
        } catch (_: Exception) {
            null
        }

    actual fun prefSet(key: String, value: String?) {
        try {
            val ls = window.localStorage
            if (value == null) {
                ls.removeItem(key)
            } else {
                ls.setItem(key, value)
            }
        } catch (_: Exception) {
        }
    }

    actual fun copyToClipboard(text: String) {
        try {
            window.navigator.clipboard.writeText(text)
        } catch (_: Exception) {
        }
    }

    actual fun applyThemeMode(mode: Int) {
    }

    actual fun appIcon(): Any? = null

    actual fun hasDynamicColor(): Boolean = false
}

@Composable
actual fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
}
