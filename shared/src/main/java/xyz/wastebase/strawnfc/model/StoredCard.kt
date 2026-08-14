package xyz.wastebase.strawnfc.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredCard(
    val id: String,
    val name: String,
    val type: CardType,
    val uidHex: String?,
    val atqaHex: String? = null,
    val sakHex: String? = null,
    val ndefPayloadBase64: String? = null,
    /** Keys live in encrypted vault — never plaintext here. */
    val classicKeysPresent: Boolean = false,
    val notes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val favorite: Boolean = false,
    val emulateStatus: EmulationCapability = EmulationCapability.UNKNOWN,
) {
    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        fun fromJson(text: String): StoredCard = json.decodeFromString(text)

        fun listFromJson(text: String): List<StoredCard> = json.decodeFromString(text)
    }

    fun toJson(): String = json.encodeToString(this)
}

fun List<StoredCard>.toJson(): String = StoredCard.json.encodeToString(this)
