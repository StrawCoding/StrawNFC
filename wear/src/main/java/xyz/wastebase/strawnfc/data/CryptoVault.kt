package xyz.wastebase.strawnfc.data

/**
 * Encrypt/decrypt sensitive card blob payloads (AES-GCM).
 * Production: [AndroidKeystoreCryptoVault]. Tests: [PassthroughCryptoVault].
 */
interface CryptoVault {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

/** Test double — no encryption (never use in production builds for real secrets). */
class PassthroughCryptoVault : CryptoVault {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.copyOf()

    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext.copyOf()
}
