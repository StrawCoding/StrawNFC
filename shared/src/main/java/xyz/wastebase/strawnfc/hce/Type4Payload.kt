package xyz.wastebase.strawnfc.hce

import java.util.Base64

/**
 * Pure guard for the stored Base64 NDEF payload before any emulate session starts.
 * An oversized or malformed payload cannot build a Type 4 file, and the HCE service
 * would then silently answer 6985 while the UI claims a live session.
 */
object Type4Payload {
    fun decodeOrNull(base64: String?): ByteArray? {
        if (base64.isNullOrBlank()) return null
        return runCatching { Base64.getDecoder().decode(base64.trim()) }.getOrNull()
    }

    fun fits(payload: ByteArray?): Boolean =
        payload != null && payload.isNotEmpty() && payload.size <= Type4NdefApduHandler.MAX_NDEF_PAYLOAD

    /** True only when the stored payload can actually be served as a Type 4 NDEF file. */
    fun isEmulatable(base64: String?): Boolean = fits(decodeOrNull(base64))
}
