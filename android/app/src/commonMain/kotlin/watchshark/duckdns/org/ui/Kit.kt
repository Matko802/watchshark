package watchshark.duckdns.org.ui

import androidx.compose.runtime.Composable

expect abstract class Context

expect object Uikit {
    val ctx: Context
    val baseUrl: String
    fun nowSec(): Long
    fun monoMs(): Long
    fun localOffsetSec(epochSec: Long): Int
    fun toast(msg: String)
    fun vibrate(ms: Long)
    fun openUrl(url: String)
    fun shareText(text: String, title: String)
    fun setSessionToken(token: String?)
    fun getSessionToken(): String?
    fun prefGet(key: String): String?
    fun prefSet(key: String, value: String?)
    fun copyToClipboard(text: String)
    fun applyThemeMode(mode: Int)
    fun appIcon(): Any?
    fun hasDynamicColor(): Boolean
}

object PlatformLocal {
    val app: Context get() = Uikit.ctx
}

@Composable
expect fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit)
