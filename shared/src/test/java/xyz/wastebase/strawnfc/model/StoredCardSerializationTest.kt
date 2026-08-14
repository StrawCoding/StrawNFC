package xyz.wastebase.strawnfc.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredCardSerializationTest {

    @Test
    fun roundTrip_preservesAllFields() {
        val card = StoredCard(
            id = "11111111-2222-3333-4444-555555555555",
            name = "Lobby door",
            type = CardType.UID_ONLY,
            uidHex = "04A1B2C3",
            atqaHex = "0044",
            sakHex = "08",
            ndefPayloadBase64 = null,
            classicKeysPresent = false,
            notes = "own_only backup",
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_100_000L,
            favorite = true,
            emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
        )

        val encoded = card.toJson()
        val decoded = StoredCard.fromJson(encoded)

        assertEquals(card, decoded)
    }

    @Test
    fun listRoundTrip_preservesOrder() {
        val cards = listOf(
            StoredCard(
                id = "a",
                name = "NDEF demo",
                type = CardType.NDEF,
                uidHex = "AABBCCDD",
                ndefPayloadBase64 = "aGVsbG8=",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
                emulateStatus = EmulationCapability.SUPPORTED,
            ),
            StoredCard(
                id = "b",
                name = "DESFire gate",
                type = CardType.DESFIRE,
                uidHex = "11223344556677",
                createdAtEpochMs = 3L,
                updatedAtEpochMs = 4L,
                emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
                notes = "不可克隆",
            ),
        )

        val encoded = cards.toJson()
        val decoded = StoredCard.listFromJson(encoded)

        assertEquals(cards, decoded)
    }

    @Test
    fun uidNormalizer_stripsSeparatorsAndUppercases() {
        assertEquals("04A1B2", UidNormalizer.normalize("04:a1:b2"))
        assertEquals("04A1B2", UidNormalizer.normalize("04-a1-b2"))
        assertEquals("04A1B2", UidNormalizer.normalize(" 04 a1 b2 "))
    }

    @Test
    fun uidNormalizer_rejectsOddLengthAndNonHex() {
        assertTrue(UidNormalizer.isValidNormalized("04A1B2"))
        assertFalse(UidNormalizer.isValidNormalized(""))
        assertFalse(UidNormalizer.isValidNormalized("04A1B"))
        assertFalse(UidNormalizer.isValidNormalized("GG"))
        assertTrue(UidNormalizer.isCommonByteLength("AABBCCDD"))
        assertFalse(UidNormalizer.isCommonByteLength("AABB"))
    }
}
