package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("手動新增 UID", style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(8.dp))
        Text("名稱", style = MaterialTheme.typography.caption2)
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("UID（hex）", style = MaterialTheme.typography.caption2)
        BasicTextField(
            value = uidRaw,
            onValueChange = { uidRaw = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (normalized.isNotEmpty()) {
            Text("正規化：$normalized", style = MaterialTheme.typography.caption2)
        }
        if (lengthWarning) {
            Text("長度非常見 4/7/10 bytes，仍可儲存。", style = MaterialTheme.typography.caption2)
        }
        if (uidRaw.isNotEmpty() && !valid) {
            Text("UID 須為偶數長度 hex（0-9A-F）。", style = MaterialTheme.typography.caption2)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                onSave(AddCardFormResult(name = name.trim(), uidHex = normalized))
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("儲存")
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}
