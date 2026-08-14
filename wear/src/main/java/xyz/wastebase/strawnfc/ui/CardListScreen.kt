package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import xyz.wastebase.strawnfc.model.StoredCard

@Composable
fun CardListScreen(
    cards: List<StoredCard>,
    onAdd: () -> Unit,
    onBackup: () -> Unit,
    onOpen: (StoredCard) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                ListHeader {
                    Text("卡片清單")
                }
            }
            item {
                Chip(
                    label = { Text("新增 UID") },
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
            item {
                Chip(
                    label = { Text("加密備份") },
                    onClick = onBackup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (cards.isEmpty()) {
                item {
                    Text("尚無卡片。可手動輸入 UID，或用手機 Companion 掃描後同步。")
                }
                item {
                    Text("邊緣右滑：下一層返回；首頁再滑才會離開 App。")
                }
            } else {
                items(cards, key = { it.id }) { card ->
                    Chip(
                        label = {
                            Text(
                                text = buildString {
                                    if (card.favorite) append("★ ")
                                    append(card.name)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "${card.type} · ${card.uidHex ?: "—"}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = { onOpen(card) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
