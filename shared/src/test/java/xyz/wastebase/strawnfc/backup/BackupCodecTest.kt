package xyz.wastebase.strawnfc.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

class BackupCodecTest {

    private val sampleCards = listOf(
        StoredCard(
            id = "card-uid",
            name = "Lobby",
            type = CardType.UID_ONLY,
            uidHex = "04A1B2C3",
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 200L,
            favorite = true,
            emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
            notes = "僅備份",
        ),
        StoredCard(
            id = "card-ndef",
            name = "NDEF demo",
            type = CardType.NDEF,
            uidHex = "AABBCCDD",
            ndefPayloadBase64 = "aGVsbG8=",
            createdAtEpochMs = 300L,
            updatedAtEpochMs = 400L,
            emulateStatus = EmulationCapability.SUPPORTED,
        ),
        StoredCard(
            id = "card-desfire",
            name = "DESFire gate",
            type = CardType.DESFIRE,
            uidHex = "11223344556677",
            createdAtEpochMs = 500L,
            updatedAtEpochMs = 600L,
            emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
            notes = "DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。",
        ),
    )

    @Test
    fun roundTrip_preservesCards() {
        val password = "correct-horse-battery".toCharArray()
        val restored = BackupCodec.roundTrip(sampleCards, password)
        assertEquals(sampleCards, restored)
    }

    @Test
    fun export_hasMagicAndVersionHeader() {
        val blob = BackupCodec.export(sampleCards, "pw".toCharArray(), exportedAtEpochMs = 42L)
        assertTrue(blob.size > 33)
        assertEquals("SNFC", blob.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(1, blob[4].toInt())
        val envelope = BackupCodec.import(blob, "pw".toCharArray())
        assertEquals(BackupCodec.FORMAT_ID, envelope.format)
        assertEquals(42L, envelope.exportedAtEpochMs)
    }

    @Test
    fun export_isNonDeterministic_dueToSaltIv() {
        val password = "same".toCharArray()
        val a = BackupCodec.export(sampleCards, password)
        val b = BackupCodec.export(sampleCards, password)
        assertFalse(a.contentEquals(b))
        assertEquals(BackupCodec.import(a, password).cards, BackupCodec.import(b, password).cards)
    }

    @Test
    fun wrongPassword_fails() {
        val blob = BackupCodec.export(sampleCards, "right".toCharArray())
        try {
            BackupCodec.import(blob, "wrong".toCharArray())
            fail("expected decrypt failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("decrypt failed") || e.message!!.contains("password"))
        }
    }

    @Test
    fun emptyPassword_rejected() {
        try {
            BackupCodec.export(sampleCards, CharArray(0))
            fail("expected empty password rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun corruptMagic_rejected() {
        val blob = BackupCodec.export(sampleCards, "pw".toCharArray())
        blob[0] = 'X'.code.toByte()
        try {
            BackupCodec.import(blob, "pw".toCharArray())
            fail("expected magic failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("magic"))
        }
    }

    @Test
    fun fileExtension_isStrawnfc() {
        assertEquals("strawnfc", BackupCodec.FILE_EXTENSION)
        assertNotEquals("json", BackupCodec.FILE_EXTENSION)
    }

    @Test
    fun roundTrip_emptyCardList() {
        val password = "empty-ok".toCharArray()
        val blob = BackupCodec.export(emptyList(), password)
        assertTrue(BackupCodec.import(blob, password).cards.isEmpty())
        assertArrayEquals(
            BackupCodec.MAGIC.toByteArray(Charsets.US_ASCII),
            blob.copyOfRange(0, 4),
        )
    }
}
