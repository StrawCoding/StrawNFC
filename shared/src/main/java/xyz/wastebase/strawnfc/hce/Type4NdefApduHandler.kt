package xyz.wastebase.strawnfc.hce

/**
 * Minimal NFC Forum Type 4 Tag NDEF application APDU handler (pure JVM).
 *
 * AID: D2760000850101 — standard NDEF Type 4 Tag Application.
 * Used by Wear [HostApduService] for the honest NDEF emulate path only.
 */
class Type4NdefApduHandler(
    ndefMessageBytes: ByteArray,
) {
    private val ndefFile: ByteArray
    private var selected: SelectedFile = SelectedFile.NONE

    init {
        require(ndefMessageBytes.size <= MAX_NDEF_PAYLOAD) {
            "NDEF payload too large for MVP Type4 file (${ndefMessageBytes.size})"
        }
        // NDEF file = 2-byte length (NLEN) + NDEF message
        ndefFile = ByteArray(2 + ndefMessageBytes.size).also { out ->
            out[0] = ((ndefMessageBytes.size shr 8) and 0xFF).toByte()
            out[1] = (ndefMessageBytes.size and 0xFF).toByte()
            System.arraycopy(ndefMessageBytes, 0, out, 2, ndefMessageBytes.size)
        }
    }

    fun process(commandApdu: ByteArray): ByteArray {
        if (commandApdu.size < 4) return SW_WRONG_LENGTH
        val cla = commandApdu[0].toInt() and 0xFF
        val ins = commandApdu[1].toInt() and 0xFF
        val p1 = commandApdu[2].toInt() and 0xFF
        val p2 = commandApdu[3].toInt() and 0xFF

        return when {
            cla == 0x00 && ins == INS_SELECT -> handleSelect(p1, p2, commandApdu)
            cla == 0x00 && ins == INS_READ_BINARY -> handleReadBinary(p1, p2, commandApdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun handleSelect(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        val data = extractData(apdu) ?: return SW_WRONG_LENGTH
        return when {
            // SELECT by name (AID)
            p1 == 0x04 && (p2 == 0x00 || p2 == 0x0C) -> {
                if (data.contentEquals(NDEF_AID)) {
                    selected = SelectedFile.NONE
                    SW_OK
                } else {
                    SW_FILE_NOT_FOUND
                }
            }
            // SELECT by file ID
            p1 == 0x00 && (p2 == 0x0C || p2 == 0x00) -> {
                when {
                    data.contentEquals(CC_FILE_ID) -> {
                        selected = SelectedFile.CC
                        SW_OK
                    }
                    data.contentEquals(NDEF_FILE_ID) -> {
                        selected = SelectedFile.NDEF
                        SW_OK
                    }
                    else -> SW_FILE_NOT_FOUND
                }
            }
            else -> SW_CONDITIONS_NOT_SATISFIED
        }
    }

    private fun handleReadBinary(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        val offset = ((p1 and 0xFF) shl 8) or (p2 and 0xFF)
        val le = if (apdu.size >= 5) apdu[4].toInt() and 0xFF else 0
        val file = when (selected) {
            SelectedFile.CC -> ccFile()
            SelectedFile.NDEF -> ndefFile
            SelectedFile.NONE -> return SW_CONDITIONS_NOT_SATISFIED
        }
        if (offset > file.size) return SW_WRONG_P1P2
        val remaining = file.size - offset
        val length = when {
            le == 0 -> remaining.coerceAtMost(256)
            else -> le.coerceAtMost(remaining)
        }
        val slice = file.copyOfRange(offset, offset + length)
        return slice + SW_OK
    }

    private fun ccFile(): ByteArray {
        // Capability Container (minimal NFC Forum Type 4)
        val ndefLen = ndefFile.size
        return byteArrayOf(
            0x00, 0x0F, // CCLEN
            0x20, // Mapping version 2.0
            0x00, 0x3B, // MLe
            0x00, 0x34, // MLc
            0x04, // NDEF File Control TLV type
            0x06, // length
            0xE1.toByte(), 0x04, // NDEF file ID
            ((ndefLen shr 8) and 0xFF).toByte(), (ndefLen and 0xFF).toByte(), // max NDEF size
            0x00, // read access
            0x00, // write access (00 = no write in MVP)
        )
    }

    private fun extractData(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return ByteArray(0)
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return null
        return apdu.copyOfRange(5, 5 + lc)
    }

    private enum class SelectedFile { NONE, CC, NDEF }

    companion object {
        val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )
        val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        private const val INS_SELECT = 0xA4
        private const val INS_READ_BINARY = 0xB0
        private const val MAX_NDEF_PAYLOAD = 2048

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_P1P2 = byteArrayOf(0x6A, 0x86.toByte())
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())

        fun statusWordOf(response: ByteArray): Int {
            require(response.size >= 2)
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            return (sw1 shl 8) or sw2
        }
    }
}
