package xyz.wastebase.strawnfc.hce

/**
 * NFC Forum Type 4 Tag NDEF application APDU handler (pure JVM).
 *
 * Android HCE only delivers ISO-DEP APDUs after the reader SELECTs our AID.
 * Having an NDEF payload does **not** magically become a Type 4 Tag —
 * this state machine must answer:
 *
 * SELECT NDEF AID → SELECT CC (E103) → READ BINARY CC →
 * SELECT NDEF file (E104) → READ BINARY NLEN + message.
 *
 * AID: D2760000850101. Read-only (no UPDATE BINARY).
 */
class Type4NdefApduHandler(
    ndefMessageBytes: ByteArray,
) {
    private val ndefFile: ByteArray
    private var selected: SelectedFile = SelectedFile.NONE

    init {
        require(ndefMessageBytes.size <= MAX_NDEF_PAYLOAD) {
            "NDEF payload too large for Type4 file (${ndefMessageBytes.size})"
        }
        // NDEF file = 2-byte NLEN + NDEF message
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

        if (!isSupportedCla(cla)) return SW_CLA_NOT_SUPPORTED

        return when (ins) {
            INS_SELECT -> handleSelect(p1, p2, commandApdu)
            INS_READ_BINARY -> handleReadBinary(p1, p2, commandApdu)
            INS_UPDATE_BINARY -> SW_INS_NOT_SUPPORTED
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    private fun handleSelect(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        val data = extractData(apdu) ?: return SW_WRONG_LENGTH
        val wantFci = p2 == 0x00
        val noFci = p2 == 0x0C
        if (!wantFci && !noFci) return SW_CONDITIONS_NOT_SATISFIED

        return when {
            // SELECT by name (AID). Re-select is valid and deselects EF.
            p1 == 0x04 && matchesNdefAid(data) -> {
                selected = SelectedFile.NONE
                if (wantFci) fciAid() else SW_OK
            }
            p1 == 0x04 -> SW_FILE_NOT_FOUND
            // SELECT by file ID (CC / NDEF EF)
            p1 == 0x00 && data.contentEquals(CC_FILE_ID) -> {
                selected = SelectedFile.CC
                if (wantFci) fciFile(CC_FILE_ID) else SW_OK
            }
            p1 == 0x00 && data.contentEquals(NDEF_FILE_ID) -> {
                selected = SelectedFile.NDEF
                if (wantFci) fciFile(NDEF_FILE_ID) else SW_OK
            }
            p1 == 0x00 -> SW_FILE_NOT_FOUND
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

    /** Clear selection state so a new RF session cannot READ BINARY without SELECT. */
    fun reset() {
        selected = SelectedFile.NONE
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
        private const val INS_UPDATE_BINARY = 0xD6
        const val MAX_NDEF_PAYLOAD = 2048
        const val MAX_NDEF_FILE_SIZE = MAX_NDEF_PAYLOAD + 2

        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        val SW_WRONG_P1P2 = byteArrayOf(0x6A, 0x86.toByte())
        val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
        val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())

        /** ISO 7816 logical channels 0–3, no secure messaging. */
        fun isSupportedCla(cla: Int): Boolean = (cla and 0xFC) == 0x00

        fun matchesNdefAid(data: ByteArray): Boolean {
            if (data.contentEquals(NDEF_AID)) return true
            return data.size == NDEF_AID.size + 1 &&
                data.copyOf(NDEF_AID.size).contentEquals(NDEF_AID) &&
                data.last() == 0.toByte()
        }

        /**
         * NFC Forum CC (15 bytes). Write access 0xFF = read-only.
         * MLe 0x00FF so readers may READ BINARY up to 255 bytes (HCE is not a tiny chip).
         */
        fun ccFile(): ByteArray {
            val maxNdef = MAX_NDEF_FILE_SIZE
            return byteArrayOf(
                0x00, 0x0F, // CCLEN
                0x20, // Mapping version 2.0
                0x00, 0xFF.toByte(), // MLe
                0x00, 0xFF.toByte(), // MLc
                0x04, // NDEF File Control TLV
                0x06,
                0xE1.toByte(), 0x04, // NDEF file ID
                ((maxNdef shr 8) and 0xFF).toByte(),
                (maxNdef and 0xFF).toByte(),
                0x00, // read access granted
                0xFF.toByte(), // write access denied
            )
        }

        fun fciAid(): ByteArray {
            val aid = NDEF_AID
            val bodyLen = 2 + aid.size
            val out = ByteArray(2 + bodyLen + 2)
            out[0] = 0x6F
            out[1] = bodyLen.toByte()
            out[2] = 0x84.toByte()
            out[3] = aid.size.toByte()
            System.arraycopy(aid, 0, out, 4, aid.size)
            out[out.size - 2] = 0x90.toByte()
            out[out.size - 1] = 0x00
            return out
        }

        fun fciFile(fileId: ByteArray): ByteArray {
            require(fileId.size == 2)
            return byteArrayOf(
                0x6F, 0x06,
                0x83.toByte(), 0x02, fileId[0], fileId[1],
                0x90.toByte(), 0x00,
            )
        }

        fun statusWordOf(response: ByteArray): Int {
            require(response.size >= 2)
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            return (sw1 shl 8) or sw2
        }

        fun dataOf(response: ByteArray): ByteArray {
            require(response.size >= 2)
            return response.copyOfRange(0, response.size - 2)
        }
    }

    private fun extractData(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return ByteArray(0)
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return null
        return apdu.copyOfRange(5, 5 + lc)
    }

    private fun ccFile(): ByteArray = Companion.ccFile()
    private fun fciAid(): ByteArray = Companion.fciAid()
    private fun fciFile(fileId: ByteArray): ByteArray = Companion.fciFile(fileId)
    private fun matchesNdefAid(data: ByteArray): Boolean = Companion.matchesNdefAid(data)
}
