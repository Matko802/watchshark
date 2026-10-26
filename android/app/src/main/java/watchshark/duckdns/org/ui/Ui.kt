package watchshark.duckdns.org.ui

import android.content.Context
import android.widget.Toast
import watchshark.duckdns.org.data.ApiClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun fullUrl(path: String?): String? = ApiClient.fullUrl(path)
fun fmtNum(n: Long): String {
    if (n < 1000) return n.toString()
    val units = arrayOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    for ((v, s) in units) {
        if (n >= v) {
            val x = n.toDouble() / v
            return (if (x >= 100) x.toInt().toString()
            else "%.1f".format(Locale.US, x).trimEnd('0').trimEnd('.')) + s
        }
    }
    return n.toString()
}

private fun pluralAge(count: Long, unit: String): String =
    if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"

fun fmtAge(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val t = fmt.parse(s.replace('T', ' ').substringBefore('.'))?.time ?: return s
        val sec = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
        val days = sec / 86400
        when {
            sec < 60 -> pluralAge(sec, "second")
            sec < 3600 -> pluralAge(sec / 60, "minute")
            days == 0L -> pluralAge(sec / 3600, "hour")
            days < 30 -> pluralAge(days, "day")
            sec < 86400L * 365 -> pluralAge(sec / (86400L * 30), "month")
            else -> pluralAge(sec / (86400L * 365), "year")
        }
    } catch (e: Exception) {
        s
    }
}
fun fmtDur(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun fmtDateTime(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val t = utc.parse(s.replace('T', ' ').substringBefore('.')) ?: return s
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(t)
    } catch (e: Exception) {
        s
    }
}
fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
fun apiErrorMessage(e: Exception): String =
    e.message?.takeIf { it.isNotBlank() } ?: "Network error"
fun httpErrorMessage(e: Exception): String {
    try {
        val body = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
        if (!body.isNullOrEmpty()) {
            val err = com.google.gson.JsonParser.parseString(body)
                .asJsonObject?.get("error")?.asString
            if (!err.isNullOrEmpty()) return err
        }
    } catch (_: Exception) {
    }
    return apiErrorMessage(e)
}
