package xyz.wastebase.strawnfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard
import java.util.Base64

class NfcCardReaderTest {

    @Test
    fun classify_uidOnly_isDeviceUnsupported() {
        val c = NfcCardReader.classify(
            techList = listOf("android.nfc.tech.NfcA"),
            hasNdefPayload = false,
        )
        assertEquals(CardType.UID_ONLY, c.type)
        assertEquals(EmulationCapability.DEVICE_UNSUPPORTED, c.emulateStatus)
    }

    @Test
    fun classify_classic_neverImpliesKeysPresent() {
        val c = NfcCardReader.classify(
            techList = listOf(
                "android.nfc.tech.NfcA",
                "android.nfc.tech.MifareClassic",
            ),
            hasNdefPayload = false,
        )
        assertEquals(CardType.MIFARE_CLASSIC, c.type)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, c.emulateStatus)
        assertTrue(c.notes!!.contains("不自動嘗試預設金鑰"))
    }

    @Test
    fun classify_desfireName_isProtocolUnsupported() {
        val c = NfcCardReader.classify(
            techList = listOf("android.nfc.tech.IsoDep", "com.nxp.nfc.MifareDesfire"),
            hasNdefPayload = false,
        )
        assertEquals(CardType.DESFIRE, c.type)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, c.emulateStatus)
        assertTrue(c.notes!!.contains("無法也不應克隆"))
    }

    @Test
    fun classify_isoDepWithoutNdef_mapsToDesfireUnsupported() {
        val c = NfcCardReader.classify(
            techList = listOf("android.nfc.tech.IsoDep"),
            hasNdefPayload = false,
        )
        assertEquals(CardType.DESFIRE, c.type)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, c.emulateStatus)
    }

    @Test
    fun classify_isoDepWithNdefPayload_isDesfireProtocolUnsupported() {
        // Task 7: IsoDep → DESFIRE／PROTOCOL_UNSUPPORTED，即使同時有 NDEF payload
        val c = NfcCardReader.classify(
            techList = listOf("android.nfc.tech.Ndef", "android.nfc.tech.IsoDep"),
            hasNdefPayload = true,
        )
        assertEquals(CardType.DESFIRE, c.type)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, c.emulateStatus)
        assertTrue(c.notes!!.contains("無法也不應克隆"))
    }

    @Test
    fun classify_ndefWithoutIsoDep_isNdef() {
        val c = NfcCardReader.classify(
            techList = listOf("android.nfc.tech.Ndef", "android.nfc.tech.NfcA"),
            hasNdefPayload = true,
        )
        assertEquals(CardType.NDEF, c.type)
        assertEquals(EmulationCapability.UNKNOWN, c.emulateStatus)
    }

    @Test
    fun fromSnapshot_buildsStoredCardJsonRoundTrip() {
        val payload = "hello-ndef".toByteArray()
        val snapshot = NfcScanSnapshot(
            uidBytes = byteArrayOf(0x04, 0xa1.toByte(), 0xb2.toByte(), 0xc3.toByte()),
            techList = listOf("android.nfc.tech.Ndef"),
            atqaHex = "0044",
            sakHex = "00",
            ndefPayload = payload,
        )
        val card = NfcCardReader.fromSnapshot(
            snapshot,
            name = "Test NDEF",
            id = "card-ndef-1",
            nowMs = 1234L,
        )
        assertEquals(CardType.NDEF, card.type)
        assertEquals("04A1B2C3", card.uidHex)
        assertEquals(Base64.getEncoder().encodeToString(payload), card.ndefPayloadBase64)
        assertFalse(card.classicKeysPresent)

        val decoded = StoredCard.fromJson(card.toJson())
        assertEquals(card, decoded)
    }

    @Test
    fun fromSnapshot_isoDepPlusNdef_classifiesDesfireKeepsPayload() {
        val payload = "type4-or-desfire-ndef".toByteArray()
        val card = NfcCardReader.fromSnapshot(
            NfcScanSnapshot(
                uidBytes = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66),
                techList = listOf(
                    "android.nfc.tech.IsoDep",
                    "android.nfc.tech.Ndef",
                    "android.nfc.tech.NfcA",
                ),
                atqaHex = "0344",
                sakHex = "20",
                ndefPayload = payload,
            ),
            name = "IsoDep+NDEF",
            id = "iso-ndef-1",
            nowMs = 99L,
        )
        assertEquals(CardType.DESFIRE, card.type)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, card.emulateStatus)
        assertEquals(Base64.getEncoder().encodeToString(payload), card.ndefPayloadBase64)
        assertTrue(card.notes!!.contains("無法也不應克隆"))
    }

    @Test
    fun fromSnapshot_classic_keysRemainAbsent() {
        val card = NfcCardReader.fromSnapshot(
            NfcScanSnapshot(
                uidBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44),
                techList = listOf("android.nfc.tech.MifareClassic"),
            ),
            id = "classic-1",
            nowMs = 1L,
        )
        assertEquals(CardType.MIFARE_CLASSIC, card.type)
        assertFalse(card.classicKeysPresent)
        assertEquals(EmulationCapability.PROTOCOL_UNSUPPORTED, card.emulateStatus)
    }
}
