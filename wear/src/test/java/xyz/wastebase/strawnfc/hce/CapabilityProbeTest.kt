package xyz.wastebase.strawnfc.hce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

class CapabilityProbeTest {

    private fun card(
        type: CardType,
        ndef: String? = null,
        status: EmulationCapability = EmulationCapability.UNKNOWN,
    ) = StoredCard(
        id = "id",
        name = "n",
        type = type,
        uidHex = "AABBCCDD",
        ndefPayloadBase64 = ndef,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        emulateStatus = status,
    )

    private val hceReady = ProbeResult(hasNfc = true, hasHostCardEmulation = true, hceServiceRegistered = true)
    private val noHce = ProbeResult(hasNfc = true, hasHostCardEmulation = false, hceServiceRegistered = false)

    @Test
    fun uidOnly_neverSupported() {
        assertEquals(
            EmulationCapability.DEVICE_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.UID_ONLY), hceReady),
        )
    }

    @Test
    fun classic_protocolUnsupported() {
        assertEquals(
            EmulationCapability.PROTOCOL_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.MIFARE_CLASSIC), hceReady),
        )
    }

    @Test
    fun desfire_protocolUnsupported() {
        assertEquals(
            EmulationCapability.PROTOCOL_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.DESFIRE), hceReady),
        )
    }

    @Test
    fun ndef_supportedWhenProbeReady() {
        assertEquals(
            EmulationCapability.SUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.NDEF, ndef = "aGVsbG8="), hceReady),
        )
    }

    @Test
    fun ndef_deviceUnsupportedWithoutHce() {
        assertEquals(
            EmulationCapability.DEVICE_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.NDEF, ndef = "aGVsbG8="), noHce),
        )
    }

    @Test
    fun ndef_withoutPayload_protocolUnsupported() {
        assertEquals(
            EmulationCapability.PROTOCOL_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.NDEF, ndef = null), hceReady),
        )
    }

    @Test
    fun ndef_payloadTooLargeForType4File_protocolUnsupported() {
        val tooBig = java.util.Base64.getEncoder()
            .encodeToString(ByteArray(Type4NdefApduHandler.MAX_NDEF_PAYLOAD + 1))
        assertEquals(
            EmulationCapability.PROTOCOL_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.NDEF, ndef = tooBig), hceReady),
        )
    }

    @Test
    fun ndef_malformedBase64_protocolUnsupported() {
        assertEquals(
            EmulationCapability.PROTOCOL_UNSUPPORTED,
            CapabilityProbe.resolveEmulateStatus(card(CardType.NDEF, ndef = "!!!nope!!!"), hceReady),
        )
    }

    @Test
    fun probeResult_canAttemptType4() {
        assertTrue(hceReady.canAttemptType4Ndef)
        assertFalse(noHce.canAttemptType4Ndef)
    }

    @Test
    fun headlines_neverClaimDoorOpen() {
        val texts = EmulationCapability.entries.map { CapabilityProbe.honestStatusHeadline(it) } +
            EmulationCapability.entries.map { CapabilityProbe.honestActionLabel(it) }
        texts.forEach { t ->
            assertFalse(t.contains("已開門"))
            assertFalse(t.contains("unlocked", ignoreCase = true))
        }
        assertEquals("可進行 Type 4 NDEF 模擬", CapabilityProbe.honestStatusHeadline(EmulationCapability.SUPPORTED))
        assertEquals("開始模擬", CapabilityProbe.honestActionLabel(EmulationCapability.SUPPORTED))
    }
}
