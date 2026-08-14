package xyz.wastebase.strawnfc.wear

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.MaterialTheme
import xyz.wastebase.strawnfc.backup.BackupCodec
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.data.Prefs
import xyz.wastebase.strawnfc.hce.CapabilityProbe
import xyz.wastebase.strawnfc.hce.StrawHostApduService
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.ui.AddCardScreen
import xyz.wastebase.strawnfc.ui.BackupScreen
import xyz.wastebase.strawnfc.ui.CardDetailScreen
import xyz.wastebase.strawnfc.ui.CardListScreen
import xyz.wastebase.strawnfc.ui.ConsentScreen
import xyz.wastebase.strawnfc.ui.EmulateScreen
import xyz.wastebase.strawnfc.ui.WearLoadingPlaceholder
import xyz.wastebase.strawnfc.ui.WearShell

/**
 * Sealed routes without SwipeDismissableNavHost — that host crashed on launch when the
 * WearNavigator backstack was empty on some devices. Nested screens use BasicSwipeToDismissBox.
 */
sealed interface WearRoute {
    data object Consent : WearRoute
    data object List : WearRoute
    data object Add : WearRoute
    data object Backup : WearRoute
    data class Detail(val id: String) : WearRoute
    data class Emulate(val id: String) : WearRoute
}

class MainActivity : ComponentActivity() {
    private val launchIntentState = mutableStateOf<Intent?>(null)
    private val cardsEpochState = mutableStateOf(0L)

    private val cardIngestReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            cardsEpochState.value = System.currentTimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchIntentState.value = intent
        val prefs = Prefs.create(this)
        val repository = CardRepository.create(this)
        val filter = android.content.IntentFilter(xyz.wastebase.strawnfc.sync.CardIngestEvents.ACTION_CARD_UPSERTED)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(cardIngestReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cardIngestReceiver, filter)
        }
        setContent {
            val launchIntent by launchIntentState
            val cardsEpoch by cardsEpochState
            MaterialTheme {
                WearShell {
                    StrawNfcWearApp(
                        prefs = prefs,
                        repository = repository,
                        launchIntent = launchIntent,
                        cardsEpoch = cardsEpoch,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(cardIngestReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntentState.value = intent
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
private fun NestedSwipeScreen(
    routeKey: Any,
    onDismissed: () -> Unit,
    content: @Composable () -> Unit,
) {
    key(routeKey) {
        val dismissState = rememberSwipeToDismissBoxState()
        BasicSwipeToDismissBox(
            onDismissed = onDismissed,
            state = dismissState,
        ) { isBackground ->
            if (isBackground) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                content()
            }
        }
    }
}

@Composable
fun StrawNfcWearApp(
    prefs: Prefs,
    repository: CardRepository,
    launchIntent: Intent? = null,
    cardsEpoch: Long = 0L,
) {
    var route by remember {
        mutableStateOf<WearRoute>(
            if (prefs.consentAccepted) WearRoute.List else WearRoute.Consent,
        )
    }
    var cards by remember {
        mutableStateOf(
            try {
                repository.list()
            } catch (t: Throwable) {
                Log.e("StrawNFC", "list() failed on start", t)
                emptyList()
            },
        )
    }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        cards = try {
            repository.list()
        } catch (t: Throwable) {
            Log.e("StrawNFC", "list() failed", t)
            cards
        }
    }

    fun goBack() {
        route = when (val current = route) {
            WearRoute.Consent, WearRoute.List -> current
            WearRoute.Add, WearRoute.Backup -> WearRoute.List
            is WearRoute.Detail -> WearRoute.List
            is WearRoute.Emulate -> WearRoute.Detail(current.id)
        }
    }

    val canSwipeBack = route !is WearRoute.Consent && route !is WearRoute.List
    BackHandler(enabled = canSwipeBack) { goBack() }

    fun applyDeepLink(intent: Intent?) {
        if (intent == null || !prefs.consentAccepted) return
        when (intent.getStringExtra(MainActivity.EXTRA_ROUTE)) {
            MainActivity.ROUTE_EMULATE -> {
                val id = intent.getStringExtra(MainActivity.EXTRA_CARD_ID).orEmpty()
                val target = when {
                    id.isNotBlank() && repository.get(id) != null -> id
                    else -> try {
                        repository.list().firstOrNull { it.favorite }?.id
                            ?: repository.list().firstOrNull()?.id
                    } catch (_: Throwable) {
                        null
                    }
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

    LaunchedEffect(cardsEpoch) {
        if (cardsEpoch > 0L) refresh()
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

        WearRoute.Add -> NestedSwipeScreen(routeKey = "add", onDismissed = { goBack() }) {
            AddCardScreen(
                onCancel = { route = WearRoute.List },
                onSave = { card ->
                    repository.upsert(card)
                    refresh()
                    route = WearRoute.List
                },
            )
        }

        WearRoute.Backup -> NestedSwipeScreen(routeKey = "backup", onDismissed = { goBack() }) {
            BackupScreen(
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
                        val envelope = BackupCodec.import(
                            Base64.decode(b64, Base64.DEFAULT),
                            password.toCharArray(),
                        )
                        envelope.cards.forEach { repository.upsert(it) }
                        refresh()
                        backupMessage = "已匯入 ${envelope.cards.size} 張"
                    } catch (e: Exception) {
                        backupMessage = "匯入失敗：${e.message}"
                    }
                },
                onBack = { route = WearRoute.List },
            )
        }

        is WearRoute.Detail -> {
            val card = cards.find { it.id == current.id } ?: repository.get(current.id)
            LaunchedEffect(current.id, card == null) {
                if (card == null) route = WearRoute.List
            }
            if (card == null) {
                WearLoadingPlaceholder("找不到卡片")
            } else {
                NestedSwipeScreen(routeKey = "detail-${card.id}", onDismissed = { goBack() }) {
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
        }

        is WearRoute.Emulate -> {
            val card = cards.find { it.id == current.id } ?: repository.get(current.id)
            LaunchedEffect(current.id, card == null) {
                if (card == null) route = WearRoute.List
            }
            if (card == null) {
                WearLoadingPlaceholder("找不到卡片")
            } else {
                val probe = remember(card.id) { CapabilityProbe.probe(context) }
                val status = CapabilityProbe.resolveEmulateStatus(card, probe)
                var sessionActive by remember(card.id) {
                    mutableStateOf(
                        StrawHostApduService.isActive(context) &&
                            StrawHostApduService.activeCardId(context) == card.id,
                    )
                }
                LaunchedEffect(card.id, status) {
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
                NestedSwipeScreen(routeKey = "emulate-${card.id}", onDismissed = { goBack() }) {
                    EmulateScreen(
                        card = card,
                        probe = probe,
                        status = status,
                        sessionActive = sessionActive,
                        onStartNdefSession = {
                            val payload = card.ndefPayloadBase64
                            if (payload.isNullOrBlank()) return@EmulateScreen
                            StrawHostApduService.activateNdef(
                                context,
                                card.id,
                                card.name,
                                payload,
                            )
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
}
