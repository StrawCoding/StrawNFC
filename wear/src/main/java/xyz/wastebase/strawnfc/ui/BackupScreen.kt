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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import xyz.wastebase.strawnfc.backup.BackupCodec
import xyz.wastebase.strawnfc.model.StoredCard

@Composable
fun BackupScreen(
    cards: List<StoredCard>,
    hasStoredBackup: Boolean,
    lastMessage: String?,
    onExport: (password: String) -> Unit,
    onImportLast: (password: String) -> Unit,
    onBack: () -> Unit,
) {
    var passwordEnabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(BackupMode.Menu) }
    val password = if (passwordEnabled) DEMO_BACKUP_PASSWORD else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("加密備份", style = MaterialTheme.typography.title3)
        Spacer(Modifier.height(4.dp))
        Text(
            ".${BackupCodec.FILE_EXTENSION} · AES-GCM · ${cards.size} 張",
            style = MaterialTheme.typography.caption2,
        )
        lastMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.caption1)
        }
        Spacer(Modifier.height(8.dp))

        when (mode) {
            BackupMode.Menu -> {
                Button(
                    onClick = { mode = BackupMode.Export },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = cards.isNotEmpty(),
                ) { Text("匯出") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { mode = BackupMode.Import },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasStoredBackup,
                ) { Text(if (hasStoredBackup) "匯入上次備份" else "尚無本機備份") }
            }
            BackupMode.Export, BackupMode.Import -> {
                Text(
                    if (mode == BackupMode.Export) "匯出需密碼" else "匯入需密碼",
                    style = MaterialTheme.typography.caption2,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { passwordEnabled = !passwordEnabled },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (passwordEnabled) "密碼已設定（測試）" else "設定測試密碼")
                }
                Spacer(Modifier.height(6.dp))
                if (mode == BackupMode.Export) {
                    Button(
                        onClick = { onExport(password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passwordEnabled,
                    ) { Text("產生並保存備份") }
                } else {
                    Button(
                        onClick = { onImportLast(password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passwordEnabled && hasStoredBackup,
                    ) { Text("解密並合併匯入") }
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = { mode = BackupMode.Menu }, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
    }
}

private enum class BackupMode { Menu, Export, Import }

/** Wear soft-keyboard-less MVP password (same string used in unit/demo flows). */
const val DEMO_BACKUP_PASSWORD = "strawnfc-demo"
