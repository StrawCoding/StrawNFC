package xyz.wastebase.strawnfc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.wastebase.strawnfc.hce.ProbeResult
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

class EmulateScreenTest {

    private val probe = ProbeResult(
        hasNfc = true,
        hasHostCardEmulation = true,
        hceServiceRegistered = true,
    )

    @Test
    fun primaryActionAppearsBeforeHonestyEssay() {
        val blocks = emulateBlocks(nfcEnabled = true, hasControlMessage = false)
        val action = blocks.indexOf(EmulateBlock.PRIMARY_ACTION)
        val honesty = blocks.indexOf(EmulateBlock.HONESTY)
        val probeLine = blocks.indexOf(EmulateBlock.PROBE)
        assertTrue(action >= 0)
        assertTrue(action < honesty)
        assertTrue(action < probeLine)
        assertEquals(EmulateBlock.TITLE, blocks.first())
        assertEquals(EmulateBlock.BACK, blocks.last())
        assertFalse(blocks.contains(EmulateBlock.NFC_SETTINGS))
        assertFalse(blocks.contains(EmulateBlock.CONTROL))
    }

    @Test
    fun nfcOffAndControlInsertWithoutPushingActionPastHonesty() {
        val blocks = emulateBlocks(nfcEnabled = false, hasControlMessage = true)
        assertTrue(blocks.indexOf(EmulateBlock.NFC_SETTINGS) < blocks.indexOf(EmulateBlock.PRIMARY_ACTION))
        assertTrue(blocks.indexOf(EmulateBlock.PRIMARY_ACTION) < blocks.indexOf(EmulateBlock.CONTROL))
        assertTrue(blocks.indexOf(EmulateBlock.CONTROL) < blocks.indexOf(EmulateBlock.HONESTY))
    }

    @Test
    fun ndefSessionControlOnlyWhenSupportedNdef() {
        assertTrue(
            emulateShowsNdefSessionControl(CardType.NDEF, EmulationCapability.SUPPORTED),
        )
        assertFalse(
            emulateShowsNdefSessionControl(CardType.NDEF, EmulationCapability.DEVICE_UNSUPPORTED),
        )
        assertFalse(
            emulateShowsNdefSessionControl(CardType.MIFARE_CLASSIC, EmulationCapability.SUPPORTED),
        )
        assertFalse(
            emulateShowsNdefSessionControl(CardType.UID_ONLY, EmulationCapability.DEVICE_UNSUPPORTED),
        )
    }

    @Test
    fun honestyBodiesNeverClaimDoorOpen() {
        val types = CardType.entries
        val statuses = EmulationCapability.entries
        for (type in types) {
            for (status in statuses) {
                val text = emulateHonestyBody(card(type), probe, status)
                assertFalse(text, text.contains("已開門"))
                assertFalse(text, text.contains("unlocked", ignoreCase = true))
            }
        }
    }

    @Test
    fun sessionActiveShowsApduLogBeforeHonesty() {
        val blocks = emulateBlocks(nfcEnabled = true, hasControlMessage = false, sessionActive = true)
        assertTrue(blocks.indexOf(EmulateBlock.PRIMARY_ACTION) < blocks.indexOf(EmulateBlock.APDU_LOG))
        assertTrue(blocks.indexOf(EmulateBlock.APDU_LOG) < blocks.indexOf(EmulateBlock.HONESTY))
    }

    @Test
    fun supportedNdefHonestyDistinguishesPayloadFromType4() {
        val text = emulateHonestyBody(
            card(CardType.NDEF),
            probe,
            EmulationCapability.SUPPORTED,
        )
        assertTrue(text.contains("Type 4"))
        assertTrue(text.contains("有 NDEF payload ≠"))
        assertFalse(text.contains("已開門"))
    }

    @Test
    fun titleClaimsType4OnlyForSupportedNdef() {
        assertEquals("Type 4 NDEF", emulateTitle(CardType.NDEF, EmulationCapability.SUPPORTED))
        assertEquals("準備模擬", emulateTitle(CardType.NDEF, EmulationCapability.DEVICE_UNSUPPORTED))
        assertEquals("準備模擬", emulateTitle(CardType.MIFARE_CLASSIC, EmulationCapability.PROTOCOL_UNSUPPORTED))
        assertEquals("準備模擬", emulateTitle(CardType.UID_ONLY, EmulationCapability.DEVICE_UNSUPPORTED))
        assertEquals("準備模擬", emulateTitle(CardType.DESFIRE, EmulationCapability.PROTOCOL_UNSUPPORTED))
    }

    @Test
    fun detailEmulateChipComesBeforeLongNotes() {
        val withNotes = detailBlocks(hasNotes = true)
        assertTrue(withNotes.indexOf(DetailBlock.EMULATE) < withNotes.indexOf(DetailBlock.HONESTY))
        assertTrue(withNotes.indexOf(DetailBlock.EMULATE) < withNotes.indexOf(DetailBlock.NOTES))
        assertEquals(DetailBlock.TITLE, withNotes.first())
        assertFalse(detailBlocks(hasNotes = false).contains(DetailBlock.NOTES))
    }

    private fun card(type: CardType) = StoredCard(
        id = "id",
        name = "n",
        type = type,
        uidHex = "AABBCCDD",
        ndefPayloadBase64 = if (type == CardType.NDEF) "aGVsbG8=" else null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
