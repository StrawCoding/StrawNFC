package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import xyz.wastebase.strawnfc.model.UidNormalizer

data class AddCardFormResult(
    val name: String,
    val uidHex: String,
)

@Composable
fun AddCardScreen(
    onCancel: () -> Unit,
    onSave: (AddCardFormResult) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var uidRaw by remember { mutableStateOf("") }
    val normalized = UidNormalizer.normalize(uidRaw)
    val valid = name.isNotBlank() && UidNormalizer.isValidNormalized(normalized)
    val lengthWarning = valid && !UidNormalizer.isCommonByteLength(normalized)
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        ) {
            item {
                ListHeader { Text("新增卡片") }
            }
            item {
                Text("寫入本機手錶庫（僅備份／授權用途）", style = MaterialTheme.typography.caption2)
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
                    onValueChange = { uidRaw = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (normalized.isNotEmpty()) {
                item {
                    Text("正規化：$normalized", style = MaterialTheme.typography.caption2)
                }
            }
            if (lengthWarning) {
                item {
                    Text("長度非常見 4/7/10 bytes，仍可儲存。", style = MaterialTheme.typography.caption2)
                }
            }
            if (uidRaw.isNotEmpty() && !valid) {
                item {
                    Text("UID 須為偶數長度 hex（0-9A-F）。", style = MaterialTheme.typography.caption2)
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(AddCardFormResult(name = name.trim(), uidHex = normalized))
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("寫入手錶")
                }
            }
            item {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
        }
    }
}
