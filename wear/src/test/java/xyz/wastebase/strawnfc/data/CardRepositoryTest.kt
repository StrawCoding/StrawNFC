package xyz.wastebase.strawnfc.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

class CardRepositoryTest {
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        repository = CardRepository(
            vault = PassthroughCryptoVault(),
            store = InMemoryCardBlobStore(),
        )
    }

    @Test
    fun normalizeUid_matchesSharedHelper() {
        assertEquals("04A1B2", CardRepository.normalizeUid("04:a1:b2"))
    }

    @Test
    fun upsert_get_list_delete_roundTrip() {
        val card = StoredCard(
            id = "card-1",
            name = "Front gate",
            type = CardType.UID_ONLY,
            uidHex = "04:a1:b2:c3",
            createdAtEpochMs = 10L,
            updatedAtEpochMs = 10L,
            emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
        )

        val saved = repository.upsert(card)
        assertEquals("04A1B2C3", saved.uidHex)
        assertEquals(saved, repository.get("card-1"))
        assertEquals(1, repository.list().size)

        assertTrue(repository.delete("card-1"))
        assertNull(repository.get("card-1"))
        assertTrue(repository.list().isEmpty())
        assertFalse(repository.delete("card-1"))
    }

    @Test
    fun list_ordersFavoritesFirst() {
        repository.upsert(
            StoredCard(
                id = "b",
                name = "Beta",
                type = CardType.UID_ONLY,
                uidHex = "AABBCCDD",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                favorite = false,
            ),
        )
        repository.upsert(
            StoredCard(
                id = "a",
                name = "Alpha",
                type = CardType.UID_ONLY,
                uidHex = "11223344",
                createdAtEpochMs = 2L,
                updatedAtEpochMs = 2L,
                favorite = true,
            ),
        )

        val ids = repository.list().map { it.id }
        assertEquals(listOf("a", "b"), ids)
    }
}
