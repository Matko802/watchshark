package watchshark.duckdns.org.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DmOutbox {
    data class Entry(val uname: String, val text: String, val ts: Long)

    private const val PREFS = "watchshark_outbox"
    private const val KEY = "queue"
    private const val MAX = 100

    fun all(ctx: Context): List<Entry> {
        return try {
            val raw = ctx.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("uname"), o.getString("text"), o.getLong("ts"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun forPeer(ctx: Context, uname: String): List<Entry> =
        all(ctx).filter { it.uname == uname }

    fun add(ctx: Context, uname: String, text: String) {
        try {
            val list = all(ctx).toMutableList()
            list.add(Entry(uname, text, System.currentTimeMillis()))
            while (list.size > MAX) list.removeAt(0)
            val arr = JSONArray()
            for (e in list) {
                arr.put(JSONObject().put("uname", e.uname).put("text", e.text).put("ts", e.ts))
            }
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun remove(ctx: Context, ts: Long) {
        try {
            val arr = JSONArray()
            for (e in all(ctx)) {
                if (e.ts != ts) {
                    arr.put(JSONObject().put("uname", e.uname).put("text", e.text).put("ts", e.ts))
                }
            }
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun flushEntry(ctx: Context, e: Entry): Long? {
        val appCtx = ctx.applicationContext
        val peerKey = try {
            ApiClient.api.dmGetKey(e.uname).get("pubkey")?.asString
        } catch (ex: Exception) {
            if (ex is java.io.IOException) throw ex
            return null
        }
        if (peerKey.isNullOrEmpty()) return null
        val (nonce, body) = DmCrypto.encrypt(appCtx, peerKey, e.text) ?: return null
        val res = try {
            ApiClient.api.dmSend(mapOf("to" to e.uname, "nonce" to nonce, "body" to body))
        } catch (ex: Exception) {
            if (ex is java.io.IOException) throw ex
            return null
        }
        return try {
            res.get("id")?.asLong
        } catch (_: Exception) {
            -1
        }
    }
}
