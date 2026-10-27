package watchshark.duckdns.org.data

import kotlinx.browser.window
import watchshark.duckdns.org.ui.Context

actual object CrashLog {
    private var last: String? = null

    actual fun install(ctx: Context) {
    }

    actual fun record(ctx: Context, throwable: Throwable) {
        last = throwable.stackTraceToString().take(4000)
        try {
            println(throwable.stackTraceToString())
        } catch (_: Exception) {
        }
    }

    actual fun lastCrash(ctx: Context): String? = last

    actual fun clear(ctx: Context) {
        last = null
    }

    actual fun showNow(ctx: Context) {
        val text = last
        if (text != null) {
            try {
                window.alert(text)
            } catch (_: Exception) {
            }
        }
    }
}
