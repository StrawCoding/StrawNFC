package xyz.wastebase.strawnfc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.data.InMemoryCardBlobStore
import xyz.wastebase.strawnfc.data.PassthroughCryptoVault
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Phone JSON payload → Wear repository (mock Data Layer).
 */
class CardSyncIngestTest {
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        repository = CardRepository(
            vault = PassthroughCryptoVault(),
            store = InMemoryCardBlobStore(),
        )
    }

    @Test
    fun ingest_putsFakePhoneCardIntoRepository() {
        val card = StoredCard(
            id = "sync-card-1",
            name = "Phone scanned",
            type = CardType.UID_ONLY,
            uidHex = "AABBCCDD",
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 10L,
            emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
            notes = "UID-only backup",
        )
        val path = SyncPaths.cardPath(card.id)
        assertEquals("/strawnfc/cards/sync-card-1", path)
        assertTrue(path.startsWith(SyncPaths.CARDS_PREFIX))

        val saved = CardSyncIngest.ingest(path, card.toJson(), repository)
        assertEquals(card.id, saved!!.id)
        assertEquals(card, repository.get("sync-card-1"))
        assertEquals(1, repository.list().size)
    }

    @Test
    fun ingest_rejectsWrongPath() {
        val card = StoredCard(
            id = "x",
            name = "x",
            type = CardType.DESFIRE,
            uidHex = "11223344",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
        )
        assertNull(CardSyncIngest.ingest("/wrong/path", card.toJson(), repository))
        assertTrue(repository.list().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun ingest_rejectsIdMismatch() {
        val card = StoredCard(
            id = "a",
            name = "a",
            type = CardType.NDEF,
            uidHex = "01020304",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        CardSyncIngest.ingest("/strawnfc/cards/b", card.toJson(), repository)
    }
}
