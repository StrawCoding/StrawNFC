package xyz.wastebase.strawnfc.model

/**
 * UID helpers shared by mobile scan and wear manual entry.
 */
object UidNormalizer {
    private fun isHexChar(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** Strip separators and uppercase: `"04:a1:b2"` → `"04A1B2"`. */
    fun normalize(raw: String): String =
        raw.filter(::isHexChar).uppercase()

    /** Even-length hex only (0–9A–F after normalize). Empty is invalid. */
    fun isValidNormalized(uidHex: String): Boolean =
        uidHex.isNotEmpty() &&
            uidHex.length % 2 == 0 &&
            uidHex.all { it in '0'..'9' || it in 'A'..'F' }

    /**
     * Common UID lengths are 4 / 7 / 10 bytes (8 / 14 / 20 hex chars).
     * Other even lengths may still be stored with a warning.
     */
    fun isCommonByteLength(uidHex: String): Boolean {
        val bytes = uidHex.length / 2
        return bytes == 4 || bytes == 7 || bytes == 10
    }
}
