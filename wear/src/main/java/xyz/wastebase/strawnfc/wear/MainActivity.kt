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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
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
import xyz.wastebase.strawnfc.ui.WearLoadingPlaceholder
import xyz.wastebase.strawnfc.ui.WearShell
import java.util.UUID

/**
 * Wear routes. Nested screens use [SwipeDismissableNavHost] so edge-swipe pops the
 * back stack instead of finishing the Activity (root cause of 「畫面消失」).
 */
object WearRoutes {
    const val CONSENT = "consent"
    const val LIST = "list"
    const val ADD = "add"
    const val BACKUP = "backup"
    const val DETAIL = "detail/{cardId}"
    const val EMULATE = "emulate/{cardId}"

    fun detail(cardId: String) = "detail/$cardId"
    fun emulate(cardId: String) = "emulate/$cardId"
}

class MainActivity : ComponentActivity() {
    private val intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentState.value = intent
        val prefs = Prefs.create(this)
        val repository = CardRepository.create(this)
        setContent {
            val launchIntent by intentState
            MaterialTheme {
                WearShell {
                    StrawNfcWearApp(
                        prefs = prefs,
                        repository = repository,
                        launchIntent = launchIntent,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
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
    val startDestination = if (prefs.consentAccepted) WearRoutes.LIST else WearRoutes.CONSENT
    val navController = rememberSwipeDismissableNavController()
    var cards by remember { mutableStateOf(repository.list()) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        cards = repository.list()
    }

    fun navigateToList() {
        navController.navigate(WearRoutes.LIST) {
            popUpTo(WearRoutes.CONSENT) { inclusive = true }
            launchSingleTop = true
        }
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
                if (target != null) {
                    navController.navigate(WearRoutes.emulate(target)) {
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(WearRoutes.LIST) { launchSingleTop = true }
                }
            }
            MainActivity.ROUTE_BACKUP -> {
                navController.navigate(WearRoutes.BACKUP) { launchSingleTop = true }
            }
            MainActivity.ROUTE_LIST -> {
                navController.navigate(WearRoutes.LIST) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(launchIntent, prefs.consentAccepted) {
        applyDeepLink(launchIntent)
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(WearRoutes.CONSENT) {
            ConsentScreen(
                onAccept = {
                    prefs.acceptConsent()
                    navigateToList()
                    applyDeepLink(launchIntent)
                },
            )
        }

        composable(WearRoutes.LIST) {
            WearShell {
                CardListScreen(
                    cards = cards,
                    onAdd = { navController.navigate(WearRoutes.ADD) },
                    onBackup = { navController.navigate(WearRoutes.BACKUP) },
                    onOpen = { card -> navController.navigate(WearRoutes.detail(card.id)) },
                )
            }
        }

        composable(WearRoutes.ADD) {
            WearShell {
                AddCardScreen(
                    onCancel = { navController.popBackStack() },
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
                        navController.popBackStack(WearRoutes.LIST, inclusive = false)
                    },
                )
            }
        }

        composable(WearRoutes.BACKUP) {
            WearShell {
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
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = WearRoutes.DETAIL,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { entry ->
            val cardId = entry.arguments?.getString("cardId").orEmpty()
            val card = cards.find { it.id == cardId } ?: repository.get(cardId)
            // Never mutate navigation during composition — blank frame = 「畫面消失」.
            LaunchedEffect(cardId, card == null) {
                if (card == null) {
                    navController.popBackStack(WearRoutes.LIST, inclusive = false)
                }
            }
            if (card == null) {
                WearLoadingPlaceholder("找不到卡片")
            } else {
                WearShell {
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
                        onEmulate = { navController.navigate(WearRoutes.emulate(card.id)) },
                        onDelete = {
                            repository.delete(card.id)
                            refresh()
                            navController.popBackStack(WearRoutes.LIST, inclusive = false)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable(
            route = WearRoutes.EMULATE,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType }),
        ) { entry ->
            val cardId = entry.arguments?.getString("cardId").orEmpty()
            val card = cards.find { it.id == cardId } ?: repository.get(cardId)
            LaunchedEffect(cardId, card == null) {
                if (card == null) {
                    navController.popBackStack(WearRoutes.LIST, inclusive = false)
                }
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
                WearShell {
                    EmulateScreen(
                        card = card,
                        probe = probe,
                        status = status,
                        sessionActive = sessionActive,
                        onStartNdefSession = {
                            val payload = card.ndefPayloadBase64
                            if (payload.isNullOrBlank()) return@EmulateScreen
                            StrawHostApduService.activateNdef(context, card.id, card.name, payload)
                            sessionActive = true
                        },
                        onStopSession = {
                            StrawHostApduService.deactivate(context)
                            sessionActive = false
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
