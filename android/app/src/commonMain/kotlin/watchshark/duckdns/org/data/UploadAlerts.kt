package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

expect object UploadAlerts {
    fun ensureScheduled(ctx: Context)
    fun cancel(ctx: Context)
}
