package xyz.wastebase.strawnfc.wear

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.MaterialTheme
import xyz.wastebase.strawnfc.backup.BackupCodec
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.data.Prefs
import xyz.wastebase.strawnfc.hce.CapabilityProbe
import xyz.wastebase.strawnfc.hce.StrawHostApduService
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.ui.AddCardScreen
import xyz.wastebase.strawnfc.ui.BackupScreen
import xyz.wastebase.strawnfc.ui.CardDetailScreen
import xyz.wastebase.strawnfc.ui.CardListScreen
import xyz.wastebase.strawnfc.ui.ConsentScreen
import xyz.wastebase.strawnfc.ui.EmulateScreen
import java.util.UUID

private sealed interface WearRoute {
    data object Consent : WearRoute
    data object List : WearRoute
    data object Add : WearRoute
    data object Backup : WearRoute
    data class Detail(val id: String) : WearRoute
    data class Emulate(val id: String) : WearRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.create(this)
        val repository = CardRepository.create(this)
        setContent {
            MaterialTheme {
                StrawNfcWearApp(
                    prefs = prefs,
                    repository = repository,
                    launchIntent = intent,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_ROUTE = "xyz.wastebase.strawnfc.EXTRA_ROUTE"
        const val EXTRA_CARD_ID = "xyz.wastebase.strawnfc.EXTRA_CARD_ID"
        const val ROUTE_EMULATE = "emulate"
        const val ROUTE_BACKUP = "backup"
        const val ROUTE_LIST = "list"
    }
}

@Composable
fun StrawNfcWearApp(
    prefs: Prefs,
    repository: CardRepository,
    launchIntent: Intent? = null,
) {
    var route by remember {
        mutableStateOf<WearRoute>(
            if (prefs.consentAccepted) WearRoute.List else WearRoute.Consent,
        )
    }
    var cards by remember { mutableStateOf(repository.list()) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        cards = repository.list()
    }

    fun applyDeepLink(intent: Intent?) {
        if (intent == null || !prefs.consentAccepted) return
        when (intent.getStringExtra(MainActivity.EXTRA_ROUTE)) {
            MainActivity.ROUTE_EMULATE -> {
                val id = intent.getStringExtra(MainActivity.EXTRA_CARD_ID).orEmpty()
                val target = when {
                    id.isNotBlank() && repository.get(id) != null -> id
                    else -> repository.list().firstOrNull { it.favorite }?.id
                        ?: repository.list().firstOrNull()?.id
                }
                if (target != null) route = WearRoute.Emulate(target)
            }
            MainActivity.ROUTE_BACKUP -> route = WearRoute.Backup
            MainActivity.ROUTE_LIST -> route = WearRoute.List
        }
    }

    LaunchedEffect(launchIntent) {
        applyDeepLink(launchIntent)
    }

    when (val current = route) {
        WearRoute.Consent -> ConsentScreen(
            onAccept = {
                prefs.acceptConsent()
                route = WearRoute.List
                applyDeepLink(launchIntent)
            },
        )

        WearRoute.List -> CardListScreen(
            cards = cards,
            onAdd = { route = WearRoute.Add },
            onBackup = { route = WearRoute.Backup },
            onOpen = { card -> route = WearRoute.Detail(card.id) },
        )

        WearRoute.Add -> AddCardScreen(
            onCancel = { route = WearRoute.List },
            onSave = { form ->
                val now = System.currentTimeMillis()
                repository.upsert(
                    StoredCard(
                        id = UUID.randomUUID().toString(),
                        name = form.name,
                        type = CardType.UID_ONLY,
                        uidHex = form.uidHex,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                        favorite = false,
                        emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
                        notes = "手動 UID；僅備份，不宣稱可開門禁",
                    ),
                )
                refresh()
                route = WearRoute.List
            },
        )

        WearRoute.Backup -> BackupScreen(
            cards = cards,
            hasStoredBackup = !prefs.lastBackupBase64.isNullOrBlank(),
            lastMessage = backupMessage,
            onExport = { password ->
                try {
                    val blob = BackupCodec.export(cards, password.toCharArray())
                    prefs.lastBackupBase64 = Base64.encodeToString(blob, Base64.NO_WRAP)
                    backupMessage = "已匯出 ${cards.size} 張（${blob.size} bytes）"
                    refresh()
                } catch (e: Exception) {
                    backupMessage = "匯出失敗：${e.message}"
                }
            },
            onImportLast = { password ->
                val b64 = prefs.lastBackupBase64
                if (b64.isNullOrBlank()) {
                    backupMessage = "沒有本機備份"
                    return@BackupScreen
                }
                try {
                    val envelope = BackupCodec.import(Base64.decode(b64, Base64.DEFAULT), password.toCharArray())
                    envelope.cards.forEach { repository.upsert(it) }
                    refresh()
                    backupMessage = "已匯入 ${envelope.cards.size} 張"
                } catch (e: Exception) {
                    backupMessage = "匯入失敗：${e.message}"
                }
            },
            onBack = { route = WearRoute.List },
        )

        is WearRoute.Detail -> {
            val card = cards.find { it.id == current.id } ?: repository.get(current.id)
            if (card == null) {
                route = WearRoute.List
            } else {
                CardDetailScreen(
                    card = card,
                    onToggleFavorite = {
                        repository.upsert(
                            card.copy(
                                favorite = !card.favorite,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                        refresh()
                    },
                    onEmulate = { route = WearRoute.Emulate(card.id) },
                    onDelete = {
                        repository.delete(card.id)
                        refresh()
                        route = WearRoute.List
                    },
                    onBack = { route = WearRoute.List },
                )
            }
        }

        is WearRoute.Emulate -> {
            val card = cards.find { it.id == current.id } ?: repository.get(current.id)
            if (card == null) {
                route = WearRoute.List
            } else {
                val probe = remember { CapabilityProbe.probe(context) }
                val status = CapabilityProbe.resolveEmulateStatus(card, probe)
                var sessionActive by remember {
                    mutableStateOf(
                        StrawHostApduService.isActive(context) &&
                            StrawHostApduService.activeCardId(context) == card.id,
                    )
                }
                // Persist honest status when probed
                LaunchedEffect(status) {
                    if (card.emulateStatus != status) {
                        repository.upsert(
                            card.copy(
                                emulateStatus = status,
                                updatedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                        refresh()
                    }
                }
                EmulateScreen(
                    card = card,
                    probe = probe,
                    status = status,
                    sessionActive = sessionActive,
                    onStartNdefSession = {
                        val payload = card.ndefPayloadBase64
                        if (payload.isNullOrBlank()) {
                            return@EmulateScreen
                        }
                        StrawHostApduService.activateNdef(context, card.id, card.name, payload)
                        sessionActive = true
                    },
                    onStopSession = {
                        StrawHostApduService.deactivate(context)
                        sessionActive = false
                    },
                    onBack = { route = WearRoute.Detail(card.id) },
                )
            }
        }
    }
}
