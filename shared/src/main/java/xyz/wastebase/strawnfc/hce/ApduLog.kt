package xyz.wastebase.strawnfc.hce

/**
 * Hex + short decode for HCE APDU traces (logcat and on-watch).
 * Tag must stay [TAG] so `adb logcat -s StrawNFC-HCE` is enough.
 */
object ApduLog {
    const val TAG = "StrawNFC-HCE"

    fun formatHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }

    fun describeCommand(apdu: ByteArray): String {
        if (apdu.size < 4) return "SHORT len=${apdu.size}"
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        return when (ins) {
            0xA4 -> "SELECT p1=${hex2(p1)} p2=${hex2(p2)}"
            0xB0 -> "READ BINARY off=${(p1 shl 8) or p2}"
            0xD6 -> "UPDATE BINARY off=${(p1 shl 8) or p2}"
            else -> "INS=${hex2(ins)} p1=${hex2(p1)} p2=${hex2(p2)}"
        }
    }

    fun describeResponse(response: ByteArray): String {
        if (response.size < 2) return "TX short"
        val sw = Type4NdefApduHandler.statusWordOf(response)
        val dataLen = response.size - 2
        return "SW=${"%04X".format(sw)} data=$dataLen"
    }

    fun lineRx(apdu: ByteArray): String =
        "RX ${describeCommand(apdu)} | ${formatHex(apdu)}"

    fun lineTx(response: ByteArray): String =
        "TX ${describeResponse(response)} | ${formatHex(response)}"

    private fun hex2(v: Int): String = "%02X".format(v)
}
