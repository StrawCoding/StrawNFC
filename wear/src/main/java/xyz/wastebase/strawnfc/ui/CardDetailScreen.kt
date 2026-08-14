package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

@Composable
fun CardDetailScreen(
    card: StoredCard,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(card.name, style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(6.dp))
        Text("類型：${card.type}", style = MaterialTheme.typography.body2)
        Text("UID：${card.uidHex ?: "—"}", style = MaterialTheme.typography.body2)
        Text("模擬：${honestEmulateLabel(card)}", style = MaterialTheme.typography.caption1)
        Spacer(Modifier.height(6.dp))
        Text(honestCapabilityNote(card), style = MaterialTheme.typography.caption2)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
            Text(if (card.favorite) "取消最愛" else "設為最愛")
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("刪除")
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

fun honestEmulateLabel(card: StoredCard): String =
    when (card.emulateStatus) {
        EmulationCapability.SUPPORTED -> "支援（協定路徑）"
        EmulationCapability.DEVICE_UNSUPPORTED -> "此裝置無法模擬"
        EmulationCapability.PROTOCOL_UNSUPPORTED -> "協定不支援模擬"
        EmulationCapability.UNKNOWN -> "未探測"
    }

fun honestCapabilityNote(card: StoredCard): String =
    when (card.type) {
        CardType.DESFIRE ->
            "DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。"
        CardType.MIFARE_CLASSIC ->
            "MIFARE Classic 多數機型無法在手錶模擬；不做金鑰破解。"
        CardType.UID_ONLY ->
            "Stock HCE 通常無法改寫對外 UID；僅備份＋能力探測，不宣稱可開門禁。"
        CardType.NDEF ->
            "NDEF 可走 Type 4 HCE（能力探測通過後）；本階段僅儲存。"
        CardType.UNKNOWN ->
            "未知類型：僅儲存，不宣稱可模擬。"
    }
