package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import xyz.wastebase.strawnfc.hce.CapabilityProbe
import xyz.wastebase.strawnfc.hce.ProbeResult
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Honest emulate UI — never claims door unlocked / UID spoof success.
 *
 * Round watches clip a top-aligned Column; primary action must sit before the
 * honesty essay so it is on the first screen.
 */
@Composable
fun EmulateScreen(
    card: StoredCard,
    probe: ProbeResult,
    status: EmulationCapability,
    sessionActive: Boolean,
    nfcEnabled: Boolean,
    controlMessage: String? = null,
    apduLogLines: List<String> = emptyList(),
    onStartNdefSession: () -> Unit,
    onStopSession: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onBack: () -> Unit,
) {
    WearScrollScaffold {
        emulateBlocks(
            nfcEnabled = nfcEnabled,
            hasControlMessage = !controlMessage.isNullOrBlank(),
            sessionActive = sessionActive,
        ).forEach { block ->
            item(key = block.name) {
                when (block) {
                    EmulateBlock.TITLE -> ListHeader {
                        Text(
                            text = emulateTitle(card.type, status),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EmulateBlock.IDENTITY -> Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = card.name,
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${card.type} · ${card.uidHex ?: "—"}",
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EmulateBlock.HEADLINE -> Text(
                        text = CapabilityProbe.honestStatusHeadline(status),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmulateBlock.NFC_SETTINGS -> Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "系統 NFC 關閉時無法模擬。",
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        CompactChip(
                            onClick = onOpenNfcSettings,
                            label = { Text("開啟 NFC 設定") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EmulateBlock.PRIMARY_ACTION -> EmulatePrimaryAction(
                        card = card,
                        status = status,
                        sessionActive = sessionActive,
                        nfcEnabled = nfcEnabled,
                        onStartNdefSession = onStartNdefSession,
                        onStopSession = onStopSession,
                    )
                    EmulateBlock.CONTROL -> Text(
                        text = controlMessage.orEmpty(),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmulateBlock.APDU_LOG -> Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "APDU",
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = if (apduLogLines.isEmpty()) {
                                "尚無 APDU。手機靠近後仍空白 → 問題在 HCE／AID routing，不是 Type 4 狀態機。"
                            } else {
                                apduLogLines.joinToString("\n")
                            },
                            style = MaterialTheme.typography.caption3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    EmulateBlock.HONESTY -> Text(
                        text = emulateHonestyBody(card, probe, status),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmulateBlock.PROBE -> Text(
                        text = "HCE：nfc=${probe.hasNfc} hce=${probe.hasHostCardEmulation} " +
                            "svc=${probe.hceServiceRegistered} radio=${if (nfcEnabled) "on" else "off"}",
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EmulateBlock.BACK -> CompactChip(
                        onClick = onBack,
                        label = { Text("返回") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmulatePrimaryAction(
    card: StoredCard,
    status: EmulationCapability,
    sessionActive: Boolean,
    nfcEnabled: Boolean,
    onStartNdefSession: () -> Unit,
    onStopSession: () -> Unit,
) {
    if (emulateShowsNdefSessionControl(card.type, status)) {
        if (sessionActive) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "NDEF 工作階段進行中（偏好 HCE 已請求；不代表已開門）",
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Chip(
                    onClick = onStopSession,
                    label = { Text("停止模擬") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        } else {
            Chip(
                onClick = onStartNdefSession,
                enabled = nfcEnabled,
                label = { Text(CapabilityProbe.honestActionLabel(status)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    } else {
        Text(
            text = CapabilityProbe.honestActionLabel(status),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class EmulateBlock {
    TITLE,
    IDENTITY,
    HEADLINE,
    NFC_SETTINGS,
    PRIMARY_ACTION,
    CONTROL,
    APDU_LOG,
    HONESTY,
    PROBE,
    BACK,
}

/** First-screen order: identity + action before the long honesty essay. */
fun emulateBlocks(
    nfcEnabled: Boolean,
    hasControlMessage: Boolean,
    sessionActive: Boolean = false,
): List<EmulateBlock> = buildList {
    add(EmulateBlock.TITLE)
    add(EmulateBlock.IDENTITY)
    add(EmulateBlock.HEADLINE)
    if (!nfcEnabled) add(EmulateBlock.NFC_SETTINGS)
    add(EmulateBlock.PRIMARY_ACTION)
    if (hasControlMessage) add(EmulateBlock.CONTROL)
    if (sessionActive) add(EmulateBlock.APDU_LOG)
    add(EmulateBlock.HONESTY)
    add(EmulateBlock.PROBE)
    add(EmulateBlock.BACK)
}

fun emulateShowsNdefSessionControl(
    type: CardType,
    status: EmulationCapability,
): Boolean = status == EmulationCapability.SUPPORTED && type == CardType.NDEF

/** Only claim the Type 4 path for cards that can actually take it. */
fun emulateTitle(type: CardType, status: EmulationCapability): String =
    if (emulateShowsNdefSessionControl(type, status)) "Type 4 NDEF" else "準備模擬"

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
                "開始後會以 NFC Forum Type 4 Tag 回應 SELECT AID／CC／NDEF File。有 NDEF payload ≠ 讀卡機一定認成 Type 4。讀到 NDEF ≠ 門禁已開。"
            EmulationCapability.DEVICE_UNSUPPORTED ->
                "此裝置缺少 NFC HCE 或服務未註冊（nfc=${probe.hasNfc}, hce=${probe.hasHostCardEmulation}）。僅備份。"
            else -> "NDEF payload 不足，無法組成 Type 4 Tag；僅備份。"
        }
        CardType.UNKNOWN ->
            "未知類型：僅儲存，不宣稱可模擬。"
    }
}
