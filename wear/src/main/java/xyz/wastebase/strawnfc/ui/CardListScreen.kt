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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import xyz.wastebase.strawnfc.model.StoredCard

@Composable
fun CardListScreen(
    cards: List<StoredCard>,
    onAdd: () -> Unit,
    onOpen: (StoredCard) -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
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
        if (cards.isEmpty()) {
            item {
                Text("尚無卡片。可手動輸入 UID（僅備份／授權用途）。")
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
