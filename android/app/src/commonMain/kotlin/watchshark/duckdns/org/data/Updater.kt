package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.Context

data class AppUpdate(
    val version: String,
    val notes: String,
    val url: String,
    val size: Long,
)

sealed interface UpdateCheck {
    data class Available(val update: AppUpdate) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

expect object Updater {
    var pending: AppUpdate?
    suspend fun checkForUpdate(): UpdateCheck
    fun currentVersion(ctx: Context): String
}
