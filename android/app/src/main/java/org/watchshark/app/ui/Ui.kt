package org.watchshark.app.ui

import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import coil.load
import com.google.android.material.snackbar.Snackbar
import org.watchshark.app.R
import org.watchshark.app.data.ApiClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun fullUrl(path: String?): String? = ApiClient.fullUrl(path)

@Volatile
private var videoLoader: coil.ImageLoader? = null

/** ImageLoader with video-frame decoding, so .webm thumbnails render too. */
fun videoImageLoader(ctx: Context): coil.ImageLoader {
    return videoLoader ?: synchronized(UiLock) {
        videoLoader ?: coil.ImageLoader.Builder(ctx.applicationContext)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
            .also { videoLoader = it }
    }
}

private object UiLock

fun ImageView.loadMedia(path: String?, placeholder: Int = R.drawable.ic_movie) {
    val url = fullUrl(path)
    if (url == null) {
        setImageResource(placeholder)
    } else if (WebmAvatarView.isWebm(path)) {
        // Coil core can't decode webm — use the video-frame decoder.
        load(url, videoImageLoader(context)) {
            placeholder(placeholder)
            error(placeholder)
            crossfade(true)
        }
    } else {
        load(url) {
            placeholder(placeholder)
            error(placeholder)
            crossfade(true)
        }
    }
}

fun fmtNum(n: Long): String {
    if (n < 1000) return n.toString()
    val units = arrayOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    for ((v, s) in units) {
        if (n >= v) {
            val x = n.toDouble() / v
            return (if (x >= 100) x.toInt().toString() else "%.1f".format(x).trimEnd('0').trimEnd('.')) + s
        }
    }
    return n.toString()
}

fun fmtAge(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val t = fmt.parse(s.replace('T', ' ').substringBefore('.'))?.time ?: return s
        val sec = ((System.currentTimeMillis() - t) / 1000).coerceAtLeast(0)
        when {
            sec < 60 -> "$sec seconds ago"
            sec < 3600 -> "${sec / 60} minutes ago"
            sec < 86400 -> "${sec / 3600} hours ago"
            sec < 86400 * 30 -> "${sec / 86400} days ago"
            else -> "${sec / (86400 * 30)} months ago"
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

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun android.view.View.snack(msg: String) =
    Snackbar.make(this, msg, Snackbar.LENGTH_SHORT).show()

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
