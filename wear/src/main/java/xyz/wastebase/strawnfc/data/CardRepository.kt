package xyz.wastebase.strawnfc.data

import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.model.UidNormalizer
import xyz.wastebase.strawnfc.model.toJson

/**
 * Encrypted local card inventory for Wear.
 */
class CardRepository(
    private val vault: CryptoVault,
    private val store: CardBlobStore,
) {
    @Synchronized
    fun list(): List<StoredCard> = load().sortedWith(
        compareByDescending<StoredCard> { it.favorite }
            .thenBy { it.name.lowercase() }
            .thenBy { it.createdAtEpochMs },
    )

    @Synchronized
    fun get(id: String): StoredCard? = load().find { it.id == id }

    @Synchronized
    fun upsert(card: StoredCard): StoredCard {
        val normalized = card.copy(uidHex = card.uidHex?.let { UidNormalizer.normalize(it) })
        val next = load().filterNot { it.id == normalized.id } + normalized
        persist(next)
        return normalized
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = load()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        persist(next)
        return true
    }

    private fun load(): List<StoredCard> {
        val encrypted = store.read() ?: return emptyList()
        if (encrypted.isEmpty()) return emptyList()
        val plaintext = vault.decrypt(encrypted)
        val text = plaintext.toString(Charsets.UTF_8)
        if (text.isBlank()) return emptyList()
        return StoredCard.listFromJson(text)
    }

    private fun persist(cards: List<StoredCard>) {
        val plaintext = cards.toJson().toByteArray(Charsets.UTF_8)
        store.write(vault.encrypt(plaintext))
    }

    companion object {
        fun normalizeUid(raw: String): String = UidNormalizer.normalize(raw)

        fun create(context: android.content.Context): CardRepository =
            CardRepository(
                vault = AndroidKeystoreCryptoVault.create(context),
                store = PrefsCardBlobStore.create(context),
            )
    }
}
