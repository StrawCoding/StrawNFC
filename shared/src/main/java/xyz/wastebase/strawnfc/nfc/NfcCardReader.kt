package xyz.wastebase.strawnfc.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard
import xyz.wastebase.strawnfc.model.UidNormalizer
import java.util.Base64
import java.util.UUID

/**
 * Pure snapshot of an NFC [Tag] for classification / unit tests (no hardware required).
 *
 * Classic: never auto-try default keys. DESFire / IsoDep access → PROTOCOL_UNSUPPORTED.
 */
data class NfcScanSnapshot(
    val uidBytes: ByteArray,
    val techList: List<String>,
    val atqaHex: String? = null,
    val sakHex: String? = null,
    val ndefPayload: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcScanSnapshot) return false
        return uidBytes.contentEquals(other.uidBytes) &&
            techList == other.techList &&
            atqaHex == other.atqaHex &&
            sakHex == other.sakHex &&
            ndefPayload.contentEquals(other.ndefPayload)
    }

    override fun hashCode(): Int {
        var result = uidBytes.contentHashCode()
        result = 31 * result + techList.hashCode()
        result = 31 * result + (atqaHex?.hashCode() ?: 0)
        result = 31 * result + (sakHex?.hashCode() ?: 0)
        result = 31 * result + (ndefPayload?.contentHashCode() ?: 0)
        return result
    }
}

data class CardClassification(
    val type: CardType,
    val emulateStatus: EmulationCapability,
    val notes: String?,
)

/**
 * NFC tag → [StoredCard]. Shared by phone companion and Wear on-device reader.
 * Does not crack keys or clone transit/payment cards.
 */
object NfcCardReader {
    fun snapshotFromTag(tag: Tag): NfcScanSnapshot {
        val techList = tag.techList?.toList().orEmpty()
        var atqaHex: String? = null
        var sakHex: String? = null
        runCatching {
            NfcA.get(tag)?.let { nfcA ->
                atqaHex = bytesToHex(nfcA.atqa)
                sakHex = "%02X".format(nfcA.sak.toInt() and 0xFF)
            }
        }
        val ndefPayload = readNdefPayload(tag)
        return NfcScanSnapshot(
            uidBytes = tag.id ?: ByteArray(0),
            techList = techList,
            atqaHex = atqaHex,
            sakHex = sakHex,
            ndefPayload = ndefPayload,
        )
    }

    fun fromTag(
        tag: Tag,
        name: String = defaultName(),
        id: String = UUID.randomUUID().toString(),
        nowMs: Long = System.currentTimeMillis(),
    ): StoredCard = fromSnapshot(snapshotFromTag(tag), name = name, id = id, nowMs = nowMs)

    fun fromSnapshot(
        snapshot: NfcScanSnapshot,
        name: String = defaultName(),
        id: String = UUID.randomUUID().toString(),
        nowMs: Long = System.currentTimeMillis(),
    ): StoredCard {
        val uidHex = UidNormalizer.normalize(bytesToHex(snapshot.uidBytes)).ifEmpty { null }
        val classification = classify(snapshot.techList, hasNdefPayload = snapshot.ndefPayload != null)
        val ndefB64 = snapshot.ndefPayload?.let {
            Base64.getEncoder().encodeToString(it)
        }
        return StoredCard(
            id = id,
            name = name,
            type = classification.type,
            uidHex = uidHex,
            atqaHex = snapshot.atqaHex?.uppercase(),
            sakHex = snapshot.sakHex?.uppercase(),
            ndefPayloadBase64 = ndefB64,
            classicKeysPresent = false,
            notes = classification.notes,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            favorite = false,
            emulateStatus = classification.emulateStatus,
        )
    }

    /**
     * Classification precedence (Task 7 / honest MVP):
     * 1) Explicit DESFire name → DESFIRE / PROTOCOL_UNSUPPORTED
     * 2) IsoDep → DESFIRE / PROTOCOL_UNSUPPORTED（即使同時有 NDEF payload）
     * 3) MifareClassic → MIFARE_CLASSIC（不自動嘗試預設金鑰）
     * 4) NDEF payload（無 IsoDep／DESFire）→ NDEF
     * 5) else UID_ONLY / DEVICE_UNSUPPORTED（Stock HCE ≠ UID spoof）
     */
    fun classify(techList: List<String>, hasNdefPayload: Boolean): CardClassification {
        val hasDesfireName = techList.any { tech ->
            tech.contains("MifareDesfire", ignoreCase = true) ||
                tech.contains("Desfire", ignoreCase = true)
        }
        val hasClassic = techList.any { it.contains(MifareClassic::class.java.name) || it.endsWith("MifareClassic") }
        val hasIsoDep = techList.any { it.contains(IsoDep::class.java.name) || it.endsWith("IsoDep") }

        return when {
            hasDesfireName -> CardClassification(
                type = CardType.DESFIRE,
                emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
                notes = NOTE_DESFIRE,
            )
            // Task 7: tech 含 IsoDep → DESFIRE／PROTOCOL_UNSUPPORTED（優先於 NDEF）
            hasIsoDep -> CardClassification(
                type = CardType.DESFIRE,
                emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
                notes = NOTE_DESFIRE,
            )
            hasClassic -> CardClassification(
                type = CardType.MIFARE_CLASSIC,
                emulateStatus = EmulationCapability.PROTOCOL_UNSUPPORTED,
                notes = NOTE_CLASSIC,
            )
            hasNdefPayload -> CardClassification(
                type = CardType.NDEF,
                emulateStatus = EmulationCapability.UNKNOWN,
                notes = NOTE_NDEF,
            )
            else -> CardClassification(
                type = CardType.UID_ONLY,
                emulateStatus = EmulationCapability.DEVICE_UNSUPPORTED,
                notes = NOTE_UID,
            )
        }
    }

    private fun readNdefPayload(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag) ?: return null
        return runCatching {
            ndef.connect()
            try {
                val message: NdefMessage = ndef.ndefMessage ?: return null
                message.toByteArray()
            } finally {
                runCatching { ndef.close() }
            }
        }.getOrNull()
    }

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    private fun defaultName(): String = "掃描卡片"

    const val NOTE_DESFIRE =
        "DESFire 為加密門禁，StrawNFC 只備份識別資訊，無法也不應克隆。"
    const val NOTE_CLASSIC =
        "MIFARE Classic：僅記錄 UID／類型（unsupported_emulate）；不自動嘗試預設金鑰。金鑰需使用者自行提供（二期）。"
    const val NOTE_NDEF =
        "已讀取 NDEF payload；手錶可走 Type 4 HCE（CapabilityProbe 通過後）。讀到 NDEF ≠ 門禁已開。"
    const val NOTE_UID =
        "UID-only：Stock HCE 通常無法改寫對外 UID；此裝置無法模擬此門禁。僅備份，不宣稱可開門禁。"
}
