package xyz.wastebase.strawnfc.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import xyz.wastebase.strawnfc.model.StoredCard
import java.util.concurrent.TimeUnit

/**
 * Phone → Wear Data Layer sender (`/strawnfc/cards/{id}`).
 * Requires a connected Wear node (Bluetooth companion link).
 */
class CardSender(
    private val context: Context,
    private val timeoutSeconds: Long = 15L,
) {
    /**
     * Puts [card] JSON on the Data Layer. Blocking — call off the main thread.
     * @throws NoWearNodeException if no watch is connected
     */
    fun sendBlocking(card: StoredCard) {
        val nodes = Tasks.await(
            Wearable.getNodeClient(context).connectedNodes,
            timeoutSeconds,
            TimeUnit.SECONDS,
        )
        if (nodes.isNullOrEmpty()) {
            throw NoWearNodeException()
        }
        val path = SyncPaths.cardPath(card.id)
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putString(SyncPaths.KEY_CARD_JSON, card.toJson())
            dataMap.putLong("updated_at", card.updatedAtEpochMs)
            // Force change detection even when re-sending same card after rename.
            dataMap.putLong("sent_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Tasks.await(
            Wearable.getDataClient(context).putDataItem(request),
            timeoutSeconds,
            TimeUnit.SECONDS,
        )
    }

    companion object {
        fun payloadJson(card: StoredCard): String = card.toJson()

        fun pathFor(card: StoredCard): String = SyncPaths.cardPath(card.id)

        fun defaultCardName(uidHex: String?): String {
            val tail = uidHex?.takeLast(4)?.uppercase().orEmpty()
            return if (tail.isBlank()) "掃描卡片" else "卡片 $tail"
        }
    }
}

class NoWearNodeException : IllegalStateException(
    "沒有連線的手錶。請確認手錶已配對且 StrawNFC 已安裝在手錶上。",
)
