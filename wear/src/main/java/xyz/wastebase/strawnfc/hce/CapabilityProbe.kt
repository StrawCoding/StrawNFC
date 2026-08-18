package xyz.wastebase.strawnfc.hce

import android.content.Context
import android.content.pm.PackageManager
import xyz.wastebase.strawnfc.model.CardType
import xyz.wastebase.strawnfc.model.EmulationCapability
import xyz.wastebase.strawnfc.model.StoredCard

/**
 * Honest HCE capability probe — never claims UID spoof / door unlock.
 */
data class ProbeResult(
    val hasNfc: Boolean,
    val hasHostCardEmulation: Boolean,
    val hceServiceRegistered: Boolean,
) {
    val canAttemptType4Ndef: Boolean
        get() = hasNfc && hasHostCardEmulation && hceServiceRegistered
}

object CapabilityProbe {
    const val HCE_SERVICE_CLASS = "xyz.wastebase.strawnfc.hce.StrawHostApduService"

    fun probe(context: Context): ProbeResult {
        val pm = context.packageManager
        val hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val hasHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        val registered = isHceServiceRegistered(context)
        return ProbeResult(
            hasNfc = hasNfc,
            hasHostCardEmulation = hasHce,
            hceServiceRegistered = registered,
        )
    }

    fun isHceServiceRegistered(context: Context): Boolean {
        return runCatching {
            context.packageManager.getServiceInfo(
                android.content.ComponentName(context, StrawHostApduService::class.java),
                PackageManager.GET_META_DATA,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Resolve honest emulate status for UI / storage updates.
     * UID spoof is never marked SUPPORTED on stock HCE.
     */
    fun resolveEmulateStatus(card: StoredCard, probe: ProbeResult): EmulationCapability {
        return when (card.type) {
            CardType.DESFIRE -> EmulationCapability.PROTOCOL_UNSUPPORTED
            CardType.MIFARE_CLASSIC -> EmulationCapability.PROTOCOL_UNSUPPORTED
            CardType.UID_ONLY -> EmulationCapability.DEVICE_UNSUPPORTED
            CardType.UNKNOWN -> EmulationCapability.PROTOCOL_UNSUPPORTED
            CardType.NDEF -> {
                when {
                    // Payload must actually fit a Type 4 NDEF file, else the service
                    // would answer 6985 while the UI shows a live session.
                    !Type4Payload.isEmulatable(card.ndefPayloadBase64) ->
                        EmulationCapability.PROTOCOL_UNSUPPORTED
                    !probe.canAttemptType4Ndef -> EmulationCapability.DEVICE_UNSUPPORTED
                    else -> EmulationCapability.SUPPORTED
                }
            }
        }
    }

    fun honestStatusHeadline(status: EmulationCapability): String =
        when (status) {
            EmulationCapability.SUPPORTED -> "可進行 Type 4 NDEF 模擬"
            EmulationCapability.DEVICE_UNSUPPORTED -> "此裝置無法模擬此門禁"
            EmulationCapability.PROTOCOL_UNSUPPORTED -> "協定不支援模擬（僅備份）"
            EmulationCapability.UNKNOWN -> "尚未完成能力探測"
        }

    fun honestActionLabel(status: EmulationCapability): String =
        when (status) {
            EmulationCapability.SUPPORTED -> "開始模擬"
            else -> "僅備份 — 無法模擬"
        }
}
