package watchshark

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object Auth {
    val json = ObjectMapper().registerKotlinModule()
    private val rng = SecureRandom()
    private val b64urlEnc = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDec = Base64.getUrlDecoder()

    fun b64urlEncBytes(b: ByteArray): String = b64urlEnc.encodeToString(b)
    fun b64urlDecStr(s: String): ByteArray = b64urlDec.decode(s)

    data class Claims(val sub: Long, val username: String, val iat: Long, val exp: Long)

    fun randHex(nbytes: Int): String {
        val b = ByteArray(nbytes)
        rng.nextBytes(b)
        val hex = "0123456789abcdef"
        val out = CharArray(nbytes * 2)
        for (i in b.indices) {
            val v = b[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 15]
        }
        return String(out)
    }

    fun issueToken(id: Long, username: String): String {
        val now = System.currentTimeMillis() / 1000
        val h = json.writeValueAsString(mapOf("alg" to "HS256", "typ" to "JWT"))
        val p = json.writeValueAsString(mapOf("sub" to id, "username" to username, "iat" to now, "exp" to now + 7 * 24 * 3600))
        val hs = b64urlEnc.encodeToString(h.toByteArray())
        val ps = b64urlEnc.encodeToString(p.toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Config.jwtSecret.toByteArray(), "HmacSHA256"))
        mac.update("$hs.$ps".toByteArray())
        return "$hs.$ps.${b64urlEnc.encodeToString(mac.doFinal())}"
    }

    fun verifyToken(tok: String): Triple<Long, String, Boolean> {
        val parts = tok.split(".")
        if (parts.size != 3) return Triple(0, "", false)
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(Config.jwtSecret.toByteArray(), "HmacSHA256"))
            mac.update("${parts[0]}.${parts[1]}".toByteArray())
            val sig = b64urlDec.decode(parts[2])
            if (!MessageDigest.isEqual(sig, mac.doFinal())) return Triple(0, "", false)
            val payload = b64urlDec.decode(parts[1])
            val node = json.readTree(payload)
            val sub = node.get("sub")?.asLong() ?: 0
            val username = node.get("username")?.asText() ?: ""
            val exp = node.get("exp")?.asLong() ?: 0
            if (exp <= System.currentTimeMillis() / 1000 || sub <= 0) return Triple(0, "", false)
            Triple(sub, username, true)
        } catch (_: Exception) {
            Triple(0, "", false)
        }
    }

    fun pwHash(pw: String): String? {
        return try {
            val salt = ByteArray(16)
            rng.nextBytes(salt)
            val spec = PBEKeySpec(pw.toCharArray(), salt, 210000, 256)
            val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val h = f.generateSecret(spec).encoded
            "pbkdf2\$210000\$${b64urlEnc.encodeToString(salt)}\$${b64urlEnc.encodeToString(h)}"
        } catch (_: Exception) {
            null
        }
    }

    fun pwVerify(pw: String, stored: String): Boolean {
        if (!stored.startsWith("pbkdf2\$")) return false
        val parts = stored.split("\$")
        if (parts.size != 4) return false
        val iters = parts[1].toIntOrNull() ?: return false
        if (iters <= 0 || iters > 2000000) return false
        return try {
            val salt = b64urlDec.decode(parts[2])
            val want = b64urlDec.decode(parts[3])
            if (want.size != 32) return false
            val spec = PBEKeySpec(pw.toCharArray(), salt, iters, 256)
            val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val got = f.generateSecret(spec).encoded
            MessageDigest.isEqual(got, want)
        } catch (_: Exception) {
            false
        }
    }

    fun validUsername(u: String): Boolean {
        if (u.length < 3 || u.length > 30) return false
        for (c in u) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_')) return false
        }
        return true
    }

    fun validEmail(e: String): Boolean {
        if (e.length > 120 || e.contains(" ")) return false
        val a = e.indexOf('@')
        if (a <= 0) return false
        return e.substring(a).contains(".")
    }

    fun isAdmin(uid: Long, username: String): Boolean {
        val lu = username.lowercase()
        if (Config.adminUser.isNotEmpty() && lu == Config.adminUser) return true
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT role,email FROM users WHERE id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return false
                    val role = rs.getString("role")
                    if (role == "admin") return true
                    val email = rs.getString("email") ?: ""
                    if (Config.adminUser.isNotEmpty() && email.lowercase() == Config.adminUser) return true
                    return false
                }
            }
        }
    }

    data class BanState(val banned: Boolean, val daysLeft: Double, val reason: String, val deleted: Boolean, val delReason: String)

    fun getBanState(uid: Long): BanState {
        synchronized(Db.lock) {
            Db.conn.prepareStatement("SELECT banned_until,ban_reason,deleted,deleted_reason FROM users WHERE id=?").use { ps ->
                ps.setLong(1, uid)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return BanState(false, 0.0, "", false, "")
                    val untilObj = rs.getObject("banned_until")
                    val until: Long? = (untilObj as? Number)?.toLong()
                    val breason = rs.getString("ban_reason") ?: ""
                    val del = rs.getInt("deleted")
                    val dreason = rs.getString("deleted_reason") ?: ""
                    if (del != 0) return BanState(false, 0.0, "", true, dreason)
                    val now = System.currentTimeMillis() / 1000
                    if (until != null && until > now) {
                        var dl = (until - now).toDouble() / 86400.0
                        if (until > 4102444800L) dl = -1.0
                        return BanState(true, dl, breason, false, "")
                    }
                    return BanState(false, 0.0, "", false, "")
                }
            }
        }
    }

    fun isBlockedFromUpload(uid: Long): Pair<Boolean, String> {
        val st = getBanState(uid)
        if (st.deleted) {
            return if (st.delReason.isNotEmpty()) Pair(true, "Your account has been deleted. Reason: ${st.delReason}")
            else Pair(true, "Your account has been deleted.")
        }
        if (st.banned) {
            if (st.daysLeft < 0) {
                return if (st.reason.isNotEmpty()) Pair(true, "You have been banned permanently. Reason: ${st.reason}")
                else Pair(true, "You have been banned permanently.")
            }
            val ds = "%.1f".format(st.daysLeft)
            return if (st.reason.isNotEmpty()) Pair(true, "You have been banned for $ds days. Reason: ${st.reason}")
            else Pair(true, "You have been banned for $ds days.")
        }
        return Pair(false, "")
    }
}
