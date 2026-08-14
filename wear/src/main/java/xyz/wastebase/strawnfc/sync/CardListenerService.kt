package xyz.wastebase.strawnfc.sync

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import xyz.wastebase.strawnfc.data.CardRepository

/**
 * Wear receiver for phone → watch card sync on `/strawnfc/cards/{id}`.
 * Persists to encrypted vault and notifies open UI to refresh.
 */
class CardListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = CardRepository.create(applicationContext)
        dataEvents.use { buffer ->
            for (event in buffer) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val path = event.dataItem.uri.path
                if (!SyncPaths.isCardPath(path)) continue
                val json = runCatching {
                    DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(SyncPaths.KEY_CARD_JSON)
                }.getOrNull()
                val saved = runCatching {
                    CardSyncIngest.ingest(path, json, repository)
                }.onFailure { err ->
                    Log.w(TAG, "ingest failed for $path: ${err.message}")
                }.getOrNull()
                if (saved != null) {
                    Log.i(TAG, "upserted card id=${saved.id} type=${saved.type}")
                    sendBroadcast(
                        Intent(CardIngestEvents.ACTION_CARD_UPSERTED)
                            .setPackage(packageName)
                            .putExtra(CardIngestEvents.EXTRA_CARD_ID, saved.id),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "StrawNfcCardSync"
    }
}
