package xyz.wastebase.strawnfc.data

import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM vault that does **not** use AndroidKeyStore.
 * Used when Keystore init fails on some Wear OEM builds (e.g. Xiaomi).
 * Key material lives in app-private SharedPreferences (Base64) — weaker than
 * Keystore hardware binding, but avoids crash-on-launch.
 */
class PrefsAesCryptoVault(
    private val loadOrCreateKeyBytes: () -> ByteArray,
) : CryptoVault {
    private val key: SecretKey by lazy {
        SecretKeySpec(loadOrCreateKeyBytes(), "AES")
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(4 + iv.size + encrypted.size).also { out ->
            out[0] = ((iv.size ushr 24) and 0xff).toByte()
            out[1] = ((iv.size ushr 16) and 0xff).toByte()
            out[2] = ((iv.size ushr 8) and 0xff).toByte()
            out[3] = (iv.size and 0xff).toByte()
            System.arraycopy(iv, 0, out, 4, iv.size)
            System.arraycopy(encrypted, 0, out, 4 + iv.size, encrypted.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 4) { "ciphertext too short" }
        val ivSize =
            ((ciphertext[0].toInt() and 0xff) shl 24) or
                ((ciphertext[1].toInt() and 0xff) shl 16) or
                ((ciphertext[2].toInt() and 0xff) shl 8) or
                (ciphertext[3].toInt() and 0xff)
        require(ivSize in 12..32) { "invalid IV size" }
        val iv = ciphertext.copyOfRange(4, 4 + ivSize)
        val encrypted = ciphertext.copyOfRange(4 + ivSize, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS = "strawnfc_soft_vault"
        private const val KEY_BYTES = "aes_key_b64"

        fun create(context: android.content.Context): PrefsAesCryptoVault {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            return PrefsAesCryptoVault {
                val existing = prefs.getString(KEY_BYTES, null)
                if (!existing.isNullOrBlank()) {
                    AndroidKeystoreCryptoVault.decodeFromPrefs(existing)
                } else {
                    val kg = KeyGenerator.getInstance("AES")
                    kg.init(256, SecureRandom())
                    val raw = kg.generateKey().encoded
                    prefs.edit().putString(KEY_BYTES, AndroidKeystoreCryptoVault.encodeToPrefs(raw)).apply()
                    raw
                }
            }
        }
    }
}

/**
 * Prefer AndroidKeyStore; fall back to [PrefsAesCryptoVault] when OEM Keystore
 * throws (observed on some Wear devices). Never crash app launch for vault init.
 */
fun createBestEffortCryptoVault(context: android.content.Context): CryptoVault {
    return try {
        AndroidKeystoreCryptoVault.create(context).also {
            // Force key generation / load while we can still fall back.
            it.encrypt(byteArrayOf(1, 2, 3))
        }
    } catch (t: Throwable) {
        Log.w("StrawNFC", "AndroidKeyStore vault unavailable; using prefs AES fallback", t)
        PrefsAesCryptoVault.create(context)
    }
}
