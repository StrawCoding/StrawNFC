package xyz.wastebase.strawnfc.hce

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Type4NdefApduHandlerTest {

    private val ndefMessage = byteArrayOf(
        0xD1.toByte(), 0x01, 0x08, 0x54, // NDEF text record header-ish (minimal bytes)
        0x02, 0x65, 0x6E, // status + "en"
        0x68, 0x65, 0x6C, 0x6C, 0x6F, // "hello"
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
        val body = Type4NdefApduHandler.dataOf(ndefResp)
        assertEquals(2 + ndefMessage.size, body.size)
        assertEquals(ndefMessage.size, ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF))
        assertArrayEquals(ndefMessage, body.copyOfRange(2, body.size))
    }

    @Test
    fun forumReader_selectWithLe_readsCcThenNdefInChunks() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val recovered = Type4ForumReader.readNdef(handler)
        assertArrayEquals(ndefMessage, recovered)
    }

    @Test
    fun selectAid_p2_00_returnsFci() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val select = apdu(0x00, 0xA4, 0x04, 0x00, Type4NdefApduHandler.NDEF_AID, le = 0)
        val resp = handler.process(select)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(resp))
        val fci = Type4NdefApduHandler.dataOf(resp)
        assertTrue(fci.size >= 2)
        assertEquals(0x6F, fci[0].toInt() and 0xFF)
        assertEquals(0x84, fci[2].toInt() and 0xFF)
    }

    @Test
    fun selectAid_p2_0c_returnsStatusOnly() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val select = apdu(0x00, 0xA4, 0x04, 0x0C, Type4NdefApduHandler.NDEF_AID)
        assertArrayEquals(Type4NdefApduHandler.SW_OK, handler.process(select))
    }

    @Test
    fun selectAid_trailingZero_stillMatches() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val aid = Type4NdefApduHandler.NDEF_AID + byteArrayOf(0x00)
        val select = apdu(0x00, 0xA4, 0x04, 0x0C, aid)
        assertEquals(0x9000, Type4NdefApduHandler.statusWordOf(handler.process(select)))
    }

    @Test
    fun reselectAid_deselectsEf() {
        val handler = Type4NdefApduHandler(ndefMessage)
        handler.process(apdu(0x00, 0xA4, 0x04, 0x0C, Type4NdefApduHandler.NDEF_AID))
        handler.process(apdu(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.CC_FILE_ID))
        handler.process(apdu(0x00, 0xA4, 0x04, 0x0C, Type4NdefApduHandler.NDEF_AID))
        val read = handler.process(apdu(0x00, 0xB0, 0x00, 0x00, le = 2))
        assertEquals(0x6985, Type4NdefApduHandler.statusWordOf(read))
    }

    @Test
    fun ccFile_isReadOnlyType4() {
        val cc = Type4NdefApduHandler.ccFile()
        assertEquals(15, cc.size)
        assertEquals(0x00, cc[0].toInt() and 0xFF)
        assertEquals(0x0F, cc[1].toInt() and 0xFF)
        assertEquals(0x20, cc[2].toInt() and 0xFF)
        assertEquals(0x00, cc[3].toInt() and 0xFF)
        assertEquals(0xFF, cc[4].toInt() and 0xFF) // MLe
        assertEquals(0x04, cc[7].toInt() and 0xFF)
        assertEquals(0xE1, cc[9].toInt() and 0xFF)
        assertEquals(0x04, cc[10].toInt() and 0xFF)
        assertEquals(0x00, cc[13].toInt() and 0xFF) // read
        assertEquals(0xFF, cc[14].toInt() and 0xFF) // write denied
        val maxNdef = ((cc[11].toInt() and 0xFF) shl 8) or (cc[12].toInt() and 0xFF)
        assertTrue(maxNdef >= 2 + ndefMessage.size)
    }

    @Test
    fun updateBinary_notSupported() {
        val handler = Type4NdefApduHandler(ndefMessage)
        handler.process(apdu(0x00, 0xA4, 0x04, 0x0C, Type4NdefApduHandler.NDEF_AID))
        handler.process(apdu(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.NDEF_FILE_ID))
        val update = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x02, 0x00, 0x00)
        assertEquals(0x6D00, Type4NdefApduHandler.statusWordOf(handler.process(update)))
    }

    @Test
    fun reset_clearsSelectionBetweenRfSessions() {
        val handler = Type4NdefApduHandler(ndefMessage)
        handler.process(apdu(0x00, 0xA4, 0x04, 0x0C, Type4NdefApduHandler.NDEF_AID))
        handler.process(apdu(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.NDEF_FILE_ID))
        assertEquals(
            0x9000,
            Type4NdefApduHandler.statusWordOf(handler.process(apdu(0x00, 0xB0, 0x00, 0x00, le = 2))),
        )

        handler.reset()
        assertEquals(
            0x6985,
            Type4NdefApduHandler.statusWordOf(handler.process(apdu(0x00, 0xB0, 0x00, 0x00, le = 2))),
        )
    }

    @Test
    fun unsupportedCla_returns6E00() {
        val handler = Type4NdefApduHandler(ndefMessage)
        val secureMessaging = byteArrayOf(0x0C, 0xA4.toByte(), 0x04, 0x00, 0x07) +
            Type4NdefApduHandler.NDEF_AID
        assertEquals(0x6E00, Type4NdefApduHandler.statusWordOf(handler.process(secureMessaging)))
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

    @Test
    fun apduLog_hexAndSelectDecode() {
        val select = apdu(0x00, 0xA4, 0x04, 0x00, Type4NdefApduHandler.NDEF_AID, le = 0)
        assertTrue(ApduLog.formatHex(select).startsWith("00 A4 04 00"))
        assertTrue(ApduLog.describeCommand(select).contains("SELECT"))
        assertTrue(ApduLog.lineRx(select).startsWith("RX "))
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

/**
 * NFC Forum Type 4 Tag read procedure used by Reader/Writer devices.
 * If this fails, a phone can see ISO-DEP but not NDEF.
 */
private object Type4ForumReader {
    fun readNdef(handler: Type4NdefApduHandler): ByteArray {
        val selectAid = cmd(0x00, 0xA4, 0x04, 0x00, Type4NdefApduHandler.NDEF_AID, le = 0)
        assertSw(handler.process(selectAid), 0x9000)

        val selectCc = cmd(0x00, 0xA4, 0x00, 0x0C, Type4NdefApduHandler.CC_FILE_ID)
        assertSw(handler.process(selectCc), 0x9000)

        val cc = Type4NdefApduHandler.dataOf(handler.process(cmd(0x00, 0xB0, 0x00, 0x00, le = 15)))
        assertEquals(15, cc.size)
        assertEquals(0x04, cc[7].toInt() and 0xFF)
        val fileId = byteArrayOf(cc[9], cc[10])
        val mle = ((cc[3].toInt() and 0xFF) shl 8) or (cc[4].toInt() and 0xFF)
        val maxNdef = ((cc[11].toInt() and 0xFF) shl 8) or (cc[12].toInt() and 0xFF)
        val readAcc = cc[13].toInt() and 0xFF
        assertEquals(0x00, readAcc)
        assertTrue(mle >= 15)
        assertTrue(maxNdef >= 2)

        val selectNdef = cmd(0x00, 0xA4, 0x00, 0x0C, fileId)
        assertSw(handler.process(selectNdef), 0x9000)

        val nlenBytes = Type4NdefApduHandler.dataOf(handler.process(cmd(0x00, 0xB0, 0x00, 0x00, le = 2)))
        assertEquals(2, nlenBytes.size)
        val nlen = ((nlenBytes[0].toInt() and 0xFF) shl 8) or (nlenBytes[1].toInt() and 0xFF)
        assertTrue(nlen + 2 <= maxNdef)

        val message = ByteArray(nlen)
        var copied = 0
        val chunk = mle.coerceAtMost(255).coerceAtLeast(1)
        while (copied < nlen) {
            val offset = 2 + copied
            val want = (nlen - copied).coerceAtMost(chunk)
            val part = Type4NdefApduHandler.dataOf(
                handler.process(cmd(0x00, 0xB0, offset shr 8, offset and 0xFF, le = want)),
            )
            assertTrue(part.isNotEmpty())
            System.arraycopy(part, 0, message, copied, part.size)
            copied += part.size
        }
        return message
    }

    private fun cmd(
        cla: Int,
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray = ByteArray(0),
        le: Int? = null,
    ): ByteArray {
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
            out.add((le and 0xFF).toByte())
        }
        return out.toByteArray()
    }

    private fun assertSw(resp: ByteArray, expected: Int) {
        assertEquals(expected, Type4NdefApduHandler.statusWordOf(resp))
    }
}
