package watchshark.duckdns.org.data

import watchshark.duckdns.org.ui.httpErrorMessage

object AppErrors {
    fun message(e: Throwable): String {
        return if (e is Exception) httpErrorMessage(e) else e.message?.takeIf { it.isNotBlank() } ?: "Error"
    }

    fun log(e: Throwable, where: String) {
        try {
            println("[WatchShark/$where] ${e.message}")
        } catch (_: Exception) {
        }
    }

    fun isAuth(e: Throwable): Boolean {
        val c = (e as? ApiException)?.code ?: return false
        return c == 401 || c == 403
    }
}
