package xyz.wastebase.strawnfc.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Contract tests for the JSON payload mobile puts on the Data Layer.
 * (Avoids Play Services in JVM unit tests.)
 */
class CardSenderContractTest {

    @Test
    fun pathAndPayload_matchWearIngestContract() {
        val card = StoredCard(
            id = "contract-1",
            name = "Gate",
            type = CardType.MIFARE_CLASSIC,
            uidHex = "DEADBEEF",
            classicKeysPresent = false,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
            emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
            notes = "no default keys",
        )
        assertEquals("/strawnfc/cards/contract-1", CardSender.pathFor(card))
        assertEquals(card.toJson(), CardSender.payloadJson(card))
        assertEquals(SyncPaths.KEY_CARD_JSON, "card_json")
    }
}
