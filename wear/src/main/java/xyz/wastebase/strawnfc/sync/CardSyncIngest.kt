package xyz.wastebase.strawnfc.sync

import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Pure ingest helper: Data Layer path + JSON → repository upsert.
 * Unit-testable without Play Services / WearableListenerService.
 */
object CardSyncIngest {
    fun ingest(
        path: String?,
        cardJson: String?,
        repository: CardRepository,
    ): StoredCard? {
        if (cardJson.isNullOrBlank()) return null
        if (!SyncPaths.isCardPath(path)) return null
        val pathId = SyncPaths.cardIdFromPath(path!!) ?: return null
        val card = StoredCard.fromJson(cardJson)
        require(card.id == pathId) {
            "card id mismatch: path=$pathId json=${card.id}"
        }
        return repository.upsert(card)
    }
}
