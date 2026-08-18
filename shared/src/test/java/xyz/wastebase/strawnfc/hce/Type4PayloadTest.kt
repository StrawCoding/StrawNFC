package xyz.wastebase.strawnfc.hce

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class Type4PayloadTest {

    @Test
    fun decodesValidBase64() {
        val bytes = byteArrayOf(0xD1.toByte(), 0x01, 0x02, 0x54, 0x02)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        assertArrayEquals(bytes, Type4Payload.decodeOrNull(b64))
        assertTrue(Type4Payload.isEmulatable(b64))
    }

    @Test
    fun rejectsBlankAndMalformed() {
        assertNull(Type4Payload.decodeOrNull(null))
        assertNull(Type4Payload.decodeOrNull("   "))
        assertNull(Type4Payload.decodeOrNull("!!!not-base64!!!"))
        assertFalse(Type4Payload.isEmulatable(null))
        assertFalse(Type4Payload.isEmulatable("!!!not-base64!!!"))
    }

    @Test
    fun rejectsEmptyPayload() {
        val emptyB64 = Base64.getEncoder().encodeToString(ByteArray(0))
        assertFalse(Type4Payload.isEmulatable(emptyB64))
    }

    @Test
    fun rejectsPayloadLargerThanType4File() {
        val tooBig = ByteArray(Type4NdefApduHandler.MAX_NDEF_PAYLOAD + 1) { 0x01 }
        val b64 = Base64.getEncoder().encodeToString(tooBig)
        assertFalse(Type4Payload.isEmulatable(b64))

        val atLimit = ByteArray(Type4NdefApduHandler.MAX_NDEF_PAYLOAD) { 0x01 }
        assertTrue(Type4Payload.isEmulatable(Base64.getEncoder().encodeToString(atLimit)))
    }

    @Test
    fun oversizedPayloadWouldThrowInHandler() {
        val tooBig = ByteArray(Type4NdefApduHandler.MAX_NDEF_PAYLOAD + 1)
        val thrown = runCatching { Type4NdefApduHandler(tooBig) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}
