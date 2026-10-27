package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

expect object CrashLog {
    fun install(ctx: Context)
    fun record(ctx: Context, throwable: Throwable)
    fun lastCrash(ctx: Context): String?
    fun clear(ctx: Context)
    fun showNow(ctx: Context)
}
