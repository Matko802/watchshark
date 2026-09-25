package watchshark.duckdns.org.data

import android.content.Context

object DmRepo {
    data class ChatLine(val id: Long, val mine: Boolean, val text: String, val at: String)

    suspend fun friends(ctx: Context): List<Friend> {
        DmCrypto.ensureUploaded(ctx)
        return try {
            ApiClient.api.friends().friends.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun peerKey(username: String): String? {
        return try {
            ApiClient.api.dmGetKey(username).get("pubkey")?.asString
        } catch (_: Exception) {
            null
        }
    }

    suspend fun thread(ctx: Context, username: String, afterId: Long): List<ChatLine> {
        val key = peerKey(username) ?: return emptyList()
        val res = try {
            ApiClient.api.dmThread(username, afterId, 50)
        } catch (_: Exception) {
            return emptyList()
        }
        val myId = try {
            ApiClient.api.me().user?.id ?: 0
        } catch (_: Exception) {
            0
        }
        val out = mutableListOf<ChatLine>()
        for (m in res.messages.orEmpty()) {
            val text = DmCrypto.decrypt(ctx.applicationContext, key, m.nonce, m.body)
                ?: continue
            out.add(ChatLine(m.id, m.senderId == myId, text, m.createdAt))
        }
        return out.sortedBy { it.id }
    }

    suspend fun send(ctx: Context, username: String, text: String): Long? {
        val key = peerKey(username) ?: return null
        val (nonce, body) = DmCrypto.encrypt(ctx.applicationContext, key, text) ?: return null
        val res = ApiClient.api.dmSend(mapOf("to" to username, "nonce" to nonce, "body" to body))
        return try {
            res.get("id")?.asLong
        } catch (_: Exception) {
            -1
        }
    }

    suspend fun flushOutbox(ctx: Context, onlyPeer: String? = null): Int {
        var sent = 0
        val list = if (onlyPeer != null) DmOutbox.forPeer(ctx, onlyPeer) else DmOutbox.all(ctx)
        for (e in list.take(20)) {
            val id = try {
                DmOutbox.flushEntry(ctx, e)
            } catch (_: java.io.IOException) {
                break
            } ?: continue
            DmOutbox.remove(ctx, e.ts)
            sent++
        }
        return sent
    }
}
