package xyz.wastebase.strawnfc.sync

/**
 * Local broadcast when Wear ingests a card from the phone Data Layer.
 * Package-scoped — only StrawNFC receives it.
 */
object CardIngestEvents {
    const val ACTION_CARD_UPSERTED = "xyz.wastebase.strawnfc.action.CARD_UPSERTED"
    const val EXTRA_CARD_ID = "card_id"
}
