package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

actual object Updater {
    private var pv: AppUpdate? = null

    actual var pending: AppUpdate?
        get() = pv
        set(value) {
            pv = value
        }

    actual suspend fun checkForUpdate(): UpdateCheck = UpdateCheck.UpToDate

    actual fun currentVersion(ctx: Context): String = "1.9.26"
}
