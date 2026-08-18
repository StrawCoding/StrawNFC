package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

@Composable
fun CardDetailScreen(
    card: StoredCard,
    onToggleFavorite: () -> Unit,
    onEmulate: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    WearScrollScaffold {
        detailBlocks(hasNotes = !card.notes.isNullOrBlank()).forEach { block ->
            item(key = block.name) {
                when (block) {
                    DetailBlock.TITLE -> ListHeader {
                        Text(
                            text = card.name,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DetailBlock.META -> Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "類型：${card.type}",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "UID：${card.uidHex ?: "—"}",
                            style = MaterialTheme.typography.body2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "模擬：${honestEmulateLabel(card)}",
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DetailBlock.EMULATE -> Chip(
                        onClick = onEmulate,
                        label = { Text("準備模擬") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(),
                    )
                    DetailBlock.FAVORITE -> CompactChip(
                        onClick = onToggleFavorite,
                        label = { Text(if (card.favorite) "取消最愛" else "設為最愛") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DetailBlock.HONESTY -> Text(
                        text = honestCapabilityNote(card),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DetailBlock.NOTES -> Text(
                        text = card.notes.orEmpty(),
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DetailBlock.DELETE -> CompactChip(
                        onClick = onDelete,
                        label = { Text("刪除") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DetailBlock.BACK -> CompactChip(
                        onClick = onBack,
                        label = { Text("返回") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

enum class DetailBlock {
    TITLE,
    META,
    EMULATE,
    FAVORITE,
    HONESTY,
    NOTES,
    DELETE,
    BACK,
}

fun detailBlocks(hasNotes: Boolean): List<DetailBlock> = buildList {
    add(DetailBlock.TITLE)
    add(DetailBlock.META)
    add(DetailBlock.EMULATE)
    add(DetailBlock.FAVORITE)
    add(DetailBlock.HONESTY)
    if (hasNotes) add(DetailBlock.NOTES)
    add(DetailBlock.DELETE)
    add(DetailBlock.BACK)
}

fun honestEmulateLabel(card: StoredCard): String =
    when (card.emulateStatus) {
        EmulationCapability.SUPPORTED -> "支援（NDEF Type4 路徑）"
        EmulationCapability.DEVICE_UNSUPPORTED -> "此裝置無法模擬此門禁"
        EmulationCapability.PROTOCOL_UNSUPPORTED -> "協定不支援模擬（僅備份）"
        EmulationCapability.UNKNOWN -> "未探測"
    }

fun honestCapabilityNote(card: StoredCard): String =
    when (card.type) {
        CardType.DESFIRE ->
            "DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。"
        CardType.MIFARE_CLASSIC ->
            "MIFARE Classic：多數機型無法在手錶模擬（unsupported_emulate）；不做金鑰破解。僅備份 UID／類型。"
        CardType.UID_ONLY ->
            "Stock HCE 通常無法改寫對外 UID；僅備份＋能力探測，不宣稱可開門禁。"
        CardType.NDEF ->
            "NDEF payload 可走 Type 4 Tag 模擬（HCE＋AID＋CC／NDEF File 齊才算）。讀到 NDEF ≠ 門禁已開。"
        CardType.UNKNOWN ->
            "未知類型：僅儲存，不宣稱可模擬。"
    }
