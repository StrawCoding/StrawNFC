package xyz.wastebase.strawnfc.ui

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.model.UidNormalizer
import xyz.wastebase.strawnfc.nfc.NfcCardReader
import xyz.wastebase.strawnfc.nfc.NfcModeController
import xyz.wastebase.strawnfc.nfc.NfcReaderCapability
import java.util.UUID

@Composable
fun AddCardScreen(
    onCancel: () -> Unit,
    onSave: (StoredCard) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val readerPresent = remember(context) { NfcReaderCapability.isReaderHardwarePresent(context) }

    var adapterEnabled by remember { mutableStateOf(NfcModeController.adapterEnabled(context)) }
    var name by remember { mutableStateOf("") }
    var uidRaw by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var scanHint by remember { mutableStateOf<String?>(null) }
    var scannedCard by remember { mutableStateOf<StoredCard?>(null) }

    val normalized = UidNormalizer.normalize(uidRaw)
    val manualValid = name.isNotBlank() && UidNormalizer.isValidNormalized(normalized)
    val scanValid = scannedCard != null && name.isNotBlank()
    val canSave = scanValid || manualValid
    val lengthWarning = manualValid && scannedCard == null && !UidNormalizer.isCommonByteLength(normalized)
    val listState = rememberScalingLazyListState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adapterEnabled = NfcModeController.adapterEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(readerPresent, adapterEnabled) {
        scanHint = when {
            !readerPresent -> "此手錶無 NFC 讀卡硬體；可手動輸入 UID，或用手機掃描寫入。"
            !adapterEnabled -> "NFC 已關閉。請開啟系統 NFC 後再掃描。"
            else -> "可貼卡掃描，或手動輸入 UID。掃描時會暫停 HCE 模擬。"
        }
    }

    DisposableEffect(scanning, activity) {
        if (scanning && activity != null && readerPresent) {
            when (val result = NfcModeController.enterReader(activity) { tag ->
                val card = NfcCardReader.fromTag(tag, name = name.ifBlank { "手錶掃描" })
                scannedCard = card
                uidRaw = card.uidHex.orEmpty()
                if (name.isBlank()) name = card.name
                scanning = false
                scanHint = "已讀取 ${card.type} · ${card.uidHex ?: "—"}"
            }) {
                NfcModeController.EnterResult.Ok -> Unit
                NfcModeController.EnterResult.NfcOff -> {
                    scanning = false
                    adapterEnabled = false
                    scanHint = "NFC 未開啟，請先到系統設定開啟。"
                }
                NfcModeController.EnterResult.Failed -> {
                    scanning = false
                    scanHint = "無法啟用讀卡（機型可能僅支援模擬／支付）。請手動輸入或用手機。"
                }
                NfcModeController.EnterResult.BusyEmulating -> {
                    scanning = false
                    scanHint = "模擬進行中，已改為讀卡優先；若失敗請先停止模擬。"
                }
            }
            onDispose {
                NfcModeController.leaveReader(activity)
            }
        } else {
            onDispose { }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = WearListDefaults.ContentPadding,
            autoCentering = null,
        ) {
            item {
                ListHeader { Text("新增卡片") }
            }
            item {
                Text(
                    text = scanHint.orEmpty(),
                    style = MaterialTheme.typography.caption2,
                )
            }
            if (readerPresent && !adapterEnabled) {
                item {
                    CompactChip(
                        onClick = {
                            val opened = NfcModeController.openNfcSettings(context)
                            scanHint = if (opened) {
                                "已開啟系統設定，請打開 NFC 後返回。"
                            } else {
                                "無法開啟設定，請到系統「連線／NFC」手動開啟。"
                            }
                        },
                        label = { Text("開啟 NFC 設定") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (readerPresent) {
                item {
                    Chip(
                        label = {
                            Text(if (scanning) "掃描中…再貼一次卡" else "貼卡掃描")
                        },
                        onClick = {
                            adapterEnabled = NfcModeController.adapterEnabled(context)
                            if (!adapterEnabled) {
                                scanHint = "NFC 未開啟，請先開啟系統 NFC。"
                                return@Chip
                            }
                            scanning = !scanning
                            if (scanning) {
                                scannedCard = null
                                scanHint = "靠近卡片…（讀卡時會暫停模擬）"
                            } else {
                                scanHint = "已停止掃描"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(),
                        enabled = adapterEnabled || scanning,
                    )
                }
            }
            item {
                Text("名稱", style = MaterialTheme.typography.caption2)
            }
            item {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("UID（hex）", style = MaterialTheme.typography.caption2)
            }
            item {
                BasicTextField(
                    value = uidRaw,
                    onValueChange = {
                        uidRaw = it
                        scannedCard = null
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (scannedCard != null) {
                item {
                    Text(
                        text = "來源：手錶掃描 · ${scannedCard!!.type}",
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
            if (normalized.isNotEmpty() && scannedCard == null) {
                item {
                    Text("正規化：$normalized", style = MaterialTheme.typography.caption2)
                }
            }
            if (lengthWarning) {
                item {
                    Text("長度非常見 4/7/10 bytes，仍可儲存。", style = MaterialTheme.typography.caption2)
                }
            }
            if (uidRaw.isNotEmpty() && !canSave && scannedCard == null) {
                item {
                    Text("UID 須為偶數長度 hex（0-9A-F）。", style = MaterialTheme.typography.caption2)
                }
            }
            item {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val trimmed = name.trim()
                        val card = scannedCard?.copy(
                            name = trimmed,
                            updatedAtEpochMs = now,
                        ) ?: StoredCard(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            type = CardType.UID_ONLY,
                            uidHex = normalized,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                            favorite = false,
                            emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
                            notes = "手錶手動新增；僅備份，不宣稱可開門禁",
                        )
                        onSave(card)
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("寫入手錶")
                }
            }
            item {
                CompactChip(
                    onClick = onCancel,
                    label = { Text("取消") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
