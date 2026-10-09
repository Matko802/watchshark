package watchshark.duckdns.org.data

import android.util.Log
import retrofit2.HttpException

object AppErrors {
    private const val TAG = "WatchShark"

    fun message(e: Throwable): String {
        try {
            val body = (e as? HttpException)?.response()?.errorBody()?.string()
            if (!body.isNullOrEmpty()) {
                val err = com.google.gson.JsonParser.parseString(body)
                    .asJsonObject?.get("error")?.asString
                if (!err.isNullOrEmpty()) return err
            }
        } catch (_: Exception) {
        }
        return e.message?.takeIf { it.isNotBlank() } ?: "Network error"
    }

    fun log(e: Throwable, where: String) {
        try {
            Log.w(TAG, "$where: ${e.message}", e)
        } catch (_: Exception) {
        }
    }

    fun isAuth(e: Throwable): Boolean {
        return (e as? HttpException)?.code() == 401 || (e as? HttpException)?.code() == 403
    }
}
