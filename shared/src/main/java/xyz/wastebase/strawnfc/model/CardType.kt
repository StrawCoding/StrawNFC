package xyz.wastebase.strawnfc.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    UID_ONLY,
    MIFARE_CLASSIC,
    NDEF,
    DESFIRE,
    UNKNOWN,
}
