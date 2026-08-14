package xyz.wastebase.strawnfc.hce

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Type4NdefApduHandlerTest {

    private val ndefMessage = byteArrayOf(
        0xD1.toByte(), 0x01, 0x08, 0x54, // NDEF text record header-ish (minimal bytes)
        0x02, 0x65, 0x6E, // status + "en"
        0x68, 0x65, 0x6C, 0x6C, 0x6F, // "hello" truncated to fit length field in header
    )

    @Test
    fun selectAid_thenReadNdef_roundTrip() {
        val handler = Type4NdefApduHandler(ndefMessage)

        val selectAid = apdu(0x00, 0xA4, 0x04, 0x00, Type4NdefApduHandler.NDEF_AID)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(handler.process(selectAid)))

        val selectCc = apdu(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.CC_FILE_ID)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(handler.process(selectCc)))

        val readCc = apdu(0x00, 0xB0, 0x00, 0x00, le = 15)
        val ccResp = handler.process(readCc)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(ccResp))
        assertTrue(ccResp.size > 2)

        val selectNdef = apdu(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.NDEF_FILE_ID)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(handler.process(selectNdef)))

        val readNdef = apdu(0x00, 0xB0, 0x00, 0x00, le = 2 + ndefMessage.size)
        val ndefResp = handler.process(readNdef)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(ndefResp))
        val body = ndefResp.copyOfRange(0, ndefResp.size - 2)
        assertEquals(2 + ndefMessage.size, body.size)
        assertEquals(ndefMessage.size, ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF))
        assertArrayEquals(ndefMessage, body.copyOfRange(2, body.size))
    }

    @Test
    fun unknownAid_fileNotFound() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val bad = apdu(0x00, 0xA4, 0x04, 0x00, byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        assertEquals(0x6A82, Type4NdefApduHandler.statusWordOf(handler.process(bad)))
    }

    @Test
    fun readWithoutSelect_conditionsNotSatisfied() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val read = apdu(0x00, 0xB0, 0x00, 0x00, le = 2)
        assertEquals(0x6985, Type4NdefApduHandler.statusWordOf(handler.process(read)))
    }

    private fun apdu(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray = ByteArray(0), le: Int? = null): ByteArray {
        val out = ArrayList<Byte>()
        out.add(cla.toByte())
        out.add(ins.toByte())
        out.add(p1.toByte())
        out.add(p2.toByte())
        if (data.isNotEmpty()) {
            out.add(data.size.toByte())
            data.forEach { out.add(it) }
        } else if (le != null) {
            out.add(le.toByte())
        }
        if (data.isNotEmpty() && le != null) {
            out.add(le.toByte())
        }
        return out.toByteArray()
    }
}
