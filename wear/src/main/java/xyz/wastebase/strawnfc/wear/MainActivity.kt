package xyz.wastebase.strawnfc.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.MaterialTheme
import xyz.wastebase.strawnfc.data.CardRepository
import xyz.wastebase.strawnfc.data.Prefs
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.ui.AddCardScreen
import xyz.wastebase.strawnfc.ui.CardDetailScreen
import xyz.wastebase.strawnfc.ui.CardListScreen
import xyz.wastebase.strawnfc.ui.ConsentScreen
import java.util.UUID

private sealed interface WearRoute {
    data object Consent : WearRoute
    data object List : WearRoute
    data object Add : WearRoute
    data class Detail(val id: String) : WearRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.create(this)
        val repository = CardRepository.create(this)
        setContent {
            MaterialTheme {
                StrawNfcWearApp(prefs = prefs, repository = repository)
            }
        }
    }
}

@Composable
fun StrawNfcWearApp(
    prefs: Prefs,
    repository: CardRepository,
) {
    var route by remember {
        mutableStateOf<WearRoute>(
            if (prefs.consentAccepted) WearRoute.List else WearRoute.Consent,
        )
    }
    var cards by remember { mutableStateOf(repository.list()) }

    fun refresh() {
        cards = repository.list()
    }

    when (val current = route) {
        WearRoute.Consent -> ConsentScreen(
            onAccept = {
                prefs.acceptConsent()
                route = WearRoute.List
            },
        )

        WearRoute.List -> CardListScreen(
            cards = cards,
            onAdd = { route = WearRoute.Add },
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
                        // Manual UID: honest default — Stock HCE rarely spoofs UID.
                        emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
                        notes = "手動 UID；僅備份，不宣稱可開門禁",
                    ),
                )
                refresh()
                route = WearRoute.List
            },
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
}
