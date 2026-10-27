package watchshark.duckdns.org.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.ApiException

fun fullUrl(path: String?): String? = ApiClient.fullUrl(path)

fun oneDec(x: Double): String {
    val scaled = (x * 10).toInt()
    val whole = scaled / 10
    val frac = (scaled % 10).coerceAtLeast(0)
    return "$whole.$frac"
}

fun fmtNum(n: Long): String {
    if (n < 1000) return n.toString()
    val units = arrayOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    for ((v, s) in units) {
        if (n >= v) {
            val whole = n / v
            val tenth = (n * 10 / v) % 10
            val frac = if (tenth == 0L) "" else ".$tenth"
            return "$whole$frac$s"
        }
    }
    return n.toString()
}

private fun pluralAge(count: Long, unit: String): String =
    if (count == 1L) "1 $unit ago" else "$count ${unit}s ago"

private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    var year = y
    year -= if (m <= 2) 1 else 0
    val era = if (year >= 0) year / 400 else (year - 399) / 400
    val yoe = year - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
    val z = z0 + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = (y + if (m <= 2) 1 else 0).toInt()
    return Triple(year, m.toInt(), d.toInt())
}

private fun parseUtc(s0: String): Long {
    val text = s0.replace('T', ' ').substringBefore('.')
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2}):(\\d{2})").find(text)
        ?: return -1L
    val y = m.groupValues[1].toIntOrNull() ?: 1970
    val mo = m.groupValues[2].toIntOrNull() ?: 1
    val d = m.groupValues[3].toIntOrNull() ?: 1
    val h = m.groupValues[4].toLongOrNull() ?: 0L
    val mi = m.groupValues[5].toLongOrNull() ?: 0L
    val sec = m.groupValues[6].toLongOrNull() ?: 0L
    val days = daysFromCivil(y, mo, d)
    return days * 86400 + h * 3600 + mi * 60 + sec
}

fun fmtAge(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val t = parseUtc(s)
        if (t < 0) return s
        val sec = (Uikit.nowSec() - t).coerceAtLeast(0)
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

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun fmtDateTime(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val t = parseUtc(s)
        if (t < 0) return s
        val local = t + Uikit.localOffsetSec(t)
        val days = local.floorDiv(86400)
        val rem = local.mod(86400)
        val (y, mo, d) = civilFromDays(days)
        val hour = (rem / 3600).toString().padStart(2, '0')
        val min = ((rem % 3600) / 60).toString().padStart(2, '0')
        "$d ${MONTHS[mo - 1]} $y, $hour:$min"
    } catch (e: Exception) {
        s
    }
}

fun Context.toast(msg: String) {
    Uikit.toast(msg)
}

fun apiErrorMessage(e: Exception): String =
    e.message?.takeIf { it.isNotBlank() } ?: "Network error"

fun httpErrorMessage(e: Exception): String {
    try {
        if (e is ApiException) {
            val body = e.serverBody
            if (!body.isNullOrEmpty()) {
                try {
                    val obj = Json.parseToJsonElement(body).jsonObject
                    val err = obj["error"]?.jsonPrimitive?.content
                    if (!err.isNullOrEmpty()) return err
                } catch (_: Exception) {
                }
            }
            e.message?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (e is kotlinx.serialization.SerializationException) {
            return "Bad server response"
        }
        if (e.message?.contains("UnknownHostException", ignoreCase = true) == true ||
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("Failed to connect", ignoreCase = true) == true ||
            e.message?.contains("Network is unreachable", ignoreCase = true) == true
        ) {
            return "Network error"
        }
    } catch (_: Exception) {
    }
    return e.message?.takeIf { it.isNotBlank() } ?: "Network error"
}

internal fun JsonObject.optStr(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

internal fun JsonObject.reqStr(key: String): String = optStr(key) ?: ""

internal fun JsonObject.optLong(key: String): Long? =
    optStr(key)?.trim()?.trim('"')?.toLongOrNull()

internal fun JsonObject.reqLong(key: String): Long = optLong(key) ?: 0L

internal fun JsonObject.optBool(key: String): Boolean? = optStr(key)?.let {
    it.equals("true", ignoreCase = true)
}
