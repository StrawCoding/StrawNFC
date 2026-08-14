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
import xyz.wastebase.strawnfc.hce.CapabilityProbe
import xyz.wastebase.strawnfc.hce.ProbeResult
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Honest emulate UI — never claims door unlocked / UID spoof success.
 */
@Composable
fun EmulateScreen(
    card: StoredCard,
    probe: ProbeResult,
    status: EmulationCapability,
    sessionActive: Boolean,
    onStartNdefSession: () -> Unit,
    onStopSession: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("準備模擬", style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(4.dp))
        Text(card.name, style = MaterialTheme.typography.body1)
        Text("${card.type} · ${card.uidHex ?: "—"}", style = MaterialTheme.typography.caption2)
        Spacer(Modifier.height(8.dp))
        Text(CapabilityProbe.honestStatusHeadline(status), style = MaterialTheme.typography.body2)
        Spacer(Modifier.height(4.dp))
        Text(emulateHonestyBody(card, probe, status), style = MaterialTheme.typography.caption2)
        Spacer(Modifier.height(6.dp))
        Text(
            "HCE：nfc=${probe.hasNfc} hce=${probe.hasHostCardEmulation} svc=${probe.hceServiceRegistered}",
            style = MaterialTheme.typography.caption3,
        )
        Spacer(Modifier.height(10.dp))
        when {
            status == EmulationCapability.SUPPORTED && card.type == CardType.NDEF -> {
                if (sessionActive) {
                    Text("NDEF 工作階段進行中（靠近讀卡機；不代表已開門）", style = MaterialTheme.typography.caption1)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onStopSession, modifier = Modifier.fillMaxWidth()) {
                        Text("停止模擬")
                    }
                } else {
                    Button(onClick = onStartNdefSession, modifier = Modifier.fillMaxWidth()) {
                        Text(CapabilityProbe.honestActionLabel(status))
                    }
                }
            }
            else -> {
                Text(CapabilityProbe.honestActionLabel(status), style = MaterialTheme.typography.caption1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

fun emulateHonestyBody(card: StoredCard, probe: ProbeResult, status: EmulationCapability): String {
    return when (card.type) {
        CardType.DESFIRE ->
            "DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。"
        CardType.MIFARE_CLASSIC ->
            "MIFARE Classic 多數機型無法以 Stock HCE 模擬；不做金鑰破解。狀態：$status"
        CardType.UID_ONLY ->
            "Stock HCE 通常無法改寫對外 UID；此裝置無法模擬此門禁。僅備份＋能力探測，不宣稱可開門禁。"
        CardType.NDEF -> when (status) {
            EmulationCapability.SUPPORTED ->
                "將以 Type 4 HCE 回應 NDEF（AID D2760000850101）。讀到 NDEF ≠ 門禁已開。"
            EmulationCapability.DEVICE_UNSUPPORTED ->
                "此裝置缺少 NFC HCE 或服務未註冊（nfc=${probe.hasNfc}, hce=${probe.hasHostCardEmulation}）。"
            else -> "NDEF payload 不足或協定路徑不可用；僅備份。"
        }
        CardType.UNKNOWN ->
            "未知類型：僅儲存，不宣稱可模擬。"
    }
}
