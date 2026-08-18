package xyz.wastebase.strawnfc.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
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

    WearScrollScaffold {
        item {
            ListHeader {
                Text(
                    text = "加密備份",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Text(
                ".${BackupCodec.FILE_EXTENSION} · AES-GCM · ${cards.size} 張",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!lastMessage.isNullOrBlank()) {
            item {
                Text(
                    lastMessage,
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        when (mode) {
            BackupMode.Menu -> {
                item {
                    Chip(
                        onClick = { mode = BackupMode.Export },
                        enabled = cards.isNotEmpty(),
                        label = { Text("匯出") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(),
                    )
                }
                item {
                    CompactChip(
                        onClick = { mode = BackupMode.Import },
                        enabled = hasStoredBackup,
                        label = { Text(if (hasStoredBackup) "匯入上次備份" else "尚無本機備份") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            BackupMode.Export, BackupMode.Import -> {
                item {
                    Text(
                        if (mode == BackupMode.Export) "匯出需密碼" else "匯入需密碼",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    CompactChip(
                        onClick = { passwordEnabled = !passwordEnabled },
                        label = { Text(if (passwordEnabled) "密碼已設定（測試）" else "設定測試密碼") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    if (mode == BackupMode.Export) {
                        Chip(
                            onClick = { onExport(password) },
                            enabled = passwordEnabled,
                            label = { Text("產生並保存備份") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.primaryChipColors(),
                        )
                    } else {
                        Chip(
                            onClick = { onImportLast(password) },
                            enabled = passwordEnabled && hasStoredBackup,
                            label = { Text("解密並合併匯入") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ChipDefaults.primaryChipColors(),
                        )
                    }
                }
                item {
                    CompactChip(
                        onClick = { mode = BackupMode.Menu },
                        label = { Text("取消") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            CompactChip(
                onClick = onBack,
                label = { Text("返回") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private enum class BackupMode { Menu, Export, Import }

/** Wear soft-keyboard-less MVP password (same string used in unit/demo flows). */
const val DEMO_BACKUP_PASSWORD = "strawnfc-demo"
