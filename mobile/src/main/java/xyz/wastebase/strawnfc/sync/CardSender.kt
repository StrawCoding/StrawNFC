package xyz.wastebase.strawnfc.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import xyz.wastebase.strawnfc.model.StoredCard
import java.util.concurrent.TimeUnit

/**
 * Phone → Wear Data Layer sender (`/strawnfc/cards/{id}`).
 */
class CardSender(
    private val context: Context,
    private val timeoutSeconds: Long = 15L,
) {
    /**
     * Puts [card] JSON on the Data Layer. Blocking — call off the main thread.
     */
    fun sendBlocking(card: StoredCard) {
        val path = SyncPaths.cardPath(card.id)
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString(SyncPaths.KEY_CARD_JSON, card.toJson())
            dataMap.putLong("updated_at", card.updatedAtEpochMs)
        }.asPutDataRequest().setUrgent()
        Tasks.await(
            Wearable.getDataClient(context).putDataItem(request),
            timeoutSeconds,
            TimeUnit.SECONDS,
        )
    }

    companion object {
        /** Builds the JSON payload that Wear expects (unit-testable without Play Services). */
        fun payloadJson(card: StoredCard): String = card.toJson()

        fun pathFor(card: StoredCard): String = SyncPaths.cardPath(card.id)
    }
}
