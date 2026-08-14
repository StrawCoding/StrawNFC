package xyz.wastebase.strawnfc.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.wear.MainActivity

/**
 * Wear Tile: favorite / first card shortcut → Emulate screen (honest prepare, not "unlocked").
 */
class StrawNfcTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repo = CardRepository.create(this)
        val favorite = repo.list().firstOrNull { it.favorite } ?: repo.list().firstOrNull()
        val title = favorite?.name ?: "StrawNFC"
        val subtitle = when {
            favorite == null -> "尚無卡片"
            else -> "準備模擬 · ${favorite.type}"
        }
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_emulate")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(MainActivity::class.java.name)
                            .setPackageName(packageName)
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_ROUTE,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(MainActivity.ROUTE_EMULATE)
                                    .build(),
                            )
                            .addKeyToExtraMapping(
                                MainActivity.EXTRA_CARD_ID,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(favorite?.id.orEmpty())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(12f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(title)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(18f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(subtitle)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(12f))
                            .setColor(argb(0xFFB0BEC5.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(column)
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
