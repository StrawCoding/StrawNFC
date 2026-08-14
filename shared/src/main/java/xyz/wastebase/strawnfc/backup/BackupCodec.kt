package xyz.wastebase.strawnfc.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.wastebase.strawnfc.model.StoredCard
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-derived AES-GCM codec for `.strawnfc` backup files.
 *
 * Wire format:
 * ```
 * magic(4) = "SNFC"
 * version(1) = 1
 * salt(16)
 * iv(12)
 * ciphertext+tag (AES-GCM)
 * ```
 *
 * Plaintext JSON: [BackupEnvelope] with cards list (no classic keys plaintext).
 */
object BackupCodec {
    const val MAGIC = "SNFC"
    const val FILE_EXTENSION = "strawnfc"
    const val FORMAT_VERSION: Byte = 1
    const val FORMAT_ID = "strawnfc-backup/v1"

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_LEN_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128
    private const val HEADER_LEN = 4 + 1 + SALT_LEN + IV_LEN

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val secureRandom = SecureRandom()

    fun export(cards: List<StoredCard>, password: CharArray, exportedAtEpochMs: Long = System.currentTimeMillis()): ByteArray {
        require(password.isNotEmpty()) { "password required" }
        val envelope = BackupEnvelope(
            format = FORMAT_ID,
            exportedAtEpochMs = exportedAtEpochMs,
            cards = cards,
        )
        val plaintext = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)
            ByteBuffer.allocate(HEADER_LEN + ciphertext.size)
                .put(MAGIC.toByteArray(Charsets.US_ASCII))
                .put(FORMAT_VERSION)
                .put(salt)
                .put(iv)
                .put(ciphertext)
                .array()
        } finally {
            destroyKey(key)
        }
    }

    fun import(blob: ByteArray, password: CharArray): BackupEnvelope {
        require(password.isNotEmpty()) { "password required" }
        require(blob.size > HEADER_LEN) { "backup too short" }
        val buffer = ByteBuffer.wrap(blob)
        val magicBytes = ByteArray(4)
        buffer.get(magicBytes)
        require(magicBytes.toString(Charsets.US_ASCII) == MAGIC) { "invalid magic" }
        val version = buffer.get()
        require(version == FORMAT_VERSION) { "unsupported backup version: $version" }
        val salt = ByteArray(SALT_LEN).also { buffer.get(it) }
        val iv = ByteArray(IV_LEN).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            json.decodeFromString(BackupEnvelope.serializer(), plaintext.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("decrypt failed (wrong password or corrupt file)", e)
        } finally {
            destroyKey(key)
        }
    }

    fun roundTrip(cards: List<StoredCard>, password: CharArray): List<StoredCard> {
        val blob = export(cards, password)
        return import(blob, password).cards
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun destroyKey(key: SecretKeySpec) {
        runCatching {
            val field = key.javaClass.getDeclaredField("key")
            field.isAccessible = true
            val bytes = field.get(key) as? ByteArray ?: return
            Arrays.fill(bytes, 0)
        }
    }
}

@Serializable
data class BackupEnvelope(
    val format: String,
    val exportedAtEpochMs: Long,
    val cards: List<StoredCard>,
)
