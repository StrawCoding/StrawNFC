package xyz.wastebase.strawnfc.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.wear.MainActivity

/**
 * Complication showing favorite card name; tap opens honest Emulate prepare screen.
 */
class FavoriteCardComplicationService : ComplicationDataSourceService() {
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val cards = CardRepository.create(this).list()
        val favorite = cards.firstOrNull { it.favorite } ?: cards.firstOrNull()
        val label = favorite?.name?.take(12) ?: "NFC"
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_EMULATE)
                putExtra(MainActivity.EXTRA_CARD_ID, favorite?.id.orEmpty())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        listener.onComplicationData(
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = PlainComplicationText.Builder(
                    "StrawNFC 最愛卡（準備模擬，不宣稱已開門）",
                ).build(),
            )
                .setTitle(PlainComplicationText.Builder("StrawNFC").build())
                .setTapAction(tap)
                .build(),
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("Lobby").build(),
            contentDescription = PlainComplicationText.Builder("preview").build(),
        )
            .setTitle(PlainComplicationText.Builder("StrawNFC").build())
            .build()
    }
}
