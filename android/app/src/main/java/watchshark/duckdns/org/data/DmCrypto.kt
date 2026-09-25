package watchshark.duckdns.org.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object DmCrypto {
    private const val ALIAS = "dm_ec"
    private const val PREFS = "watchshark_dm"
    private const val KEY_UPLOADED = "pub_uploaded"

    private fun keystore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun entry(): KeyStore.PrivateKeyEntry {
        val ks = keystore()
        (ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { return it }
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .build()
        )
        kpg.generateKeyPair()
        return keystore().getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
    }

    private fun pad32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val src = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
        System.arraycopy(src, 0, out, 32 - src.size, src.size)
        return out
    }

    fun myPublicKeyB64(ctx: Context): String? = try {
        val pub = entry().certificate.publicKey as java.security.interfaces.ECPublicKey
        val w = pub.w
        val raw = byteArrayOf(0x04) + pad32(w.affineX) + pad32(w.affineY)
        Base64.encodeToString(raw, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    private fun ecParams(): ECParameterSpec {
        val p = java.security.AlgorithmParameters.getInstance("EC")
        p.init(ECGenParameterSpec("secp256r1"))
        return p.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun decodePeer(b64: String): java.security.PublicKey? = try {
        val raw = Base64.decode(b64, Base64.DEFAULT)
        if (raw.size != 65 || raw[0] != 0x04.toByte()) return null
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), ecParams()))
    } catch (_: Exception) {
        null
    }

    private fun cmpBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    private fun sharedKey(ctx: Context, peerB64: String): SecretKeySpec? = try {
        val peer = decodePeer(peerB64) ?: return null
        val mine = Base64.decode(myPublicKeyB64(ctx) ?: return null, Base64.DEFAULT)
        val theirs = Base64.decode(peerB64, Base64.DEFAULT)
        val (first, second) = if (cmpBytes(mine, theirs) <= 0) mine to theirs else theirs to mine
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(entry().privateKey)
        ka.doPhase(peer, true)
        val z = ka.generateSecret()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(z)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update("ws-dm-v1".toByteArray())
        mac.update(first)
        mac.update(second)
        mac.update(byteArrayOf(1))
        SecretKeySpec(mac.doFinal(), "AES")
    } catch (_: Exception) {
        null
    }

    fun encrypt(ctx: Context, peerB64: String, plain: String): Pair<String, String>? = try {
        val key = sharedKey(ctx, peerB64) ?: return null
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(nonce, Base64.NO_WRAP) to
            Base64.encodeToString(body, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    fun decrypt(ctx: Context, peerB64: String, nonceB64: String, bodyB64: String): String? = try {
        val key = sharedKey(ctx, peerB64) ?: return null
        val nonce = Base64.decode(nonceB64, Base64.DEFAULT)
        val body = Base64.decode(bodyB64, Base64.DEFAULT)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        String(c.doFinal(body), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun isUploaded(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_UPLOADED, false)

    fun markUploaded(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_UPLOADED, true).apply()
    }

    suspend fun ensureUploaded(ctx: Context): Boolean {
        if (isUploaded(ctx)) return true
        return try {
            val pub = myPublicKeyB64(ctx) ?: return false
            ApiClient.api.dmSetKey(mapOf("pubkey" to pub))
            markUploaded(ctx)
            true
        } catch (_: Exception) {
            false
        }
    }
}
