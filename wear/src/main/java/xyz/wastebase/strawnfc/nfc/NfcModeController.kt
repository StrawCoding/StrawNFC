package xyz.wastebase.strawnfc.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.Tag
import android.nfc.cardemulation.CardEmulation
import android.util.Log
import xyz.wastebase.strawnfc.hce.StrawHostApduService

/**
 * Single owner of Wear NFC foreground mode: Idle ↔ Reader ↔ HCE.
 * Ensures ReaderMode and HostApduService do not fight for the radio,
 * and requests preferred HCE routing while emulating (category=other).
 */
object NfcModeController {
    private const val TAG = "StrawNFC-NfcCtrl"

    @Volatile
    private var mode: NfcWorkMode = NfcWorkMode.IDLE

    private val lock = Any()

    fun currentMode(): NfcWorkMode = mode

    sealed interface EnterResult {
        data object Ok : EnterResult
        data object NfcOff : EnterResult
        data object Failed : EnterResult
        data object BusyEmulating : EnterResult
    }

    fun enterReader(activity: Activity, onTag: (Tag) -> Unit): EnterResult =
        synchronized(lock) {
            if (!NfcReaderCapability.isAdapterEnabled(activity)) {
                return EnterResult.NfcOff
            }
            val plan = NfcModePolicy.transition(mode, NfcModeAction.StartReader)
            if (plan.stopHce) stopHceInternal(activity)
            if (plan.stopReader) NfcReaderSession.disable(activity)

            val ok = NfcReaderSession.enable(activity, onTag = onTag)
            if (!ok) {
                mode = NfcWorkMode.IDLE
                Log.w(TAG, "enableReaderMode failed")
                return EnterResult.Failed
            }
            mode = NfcWorkMode.READER
            EnterResult.Ok
        }

    fun leaveReader(activity: Activity) =
        synchronized(lock) {
            val plan = NfcModePolicy.transition(mode, NfcModeAction.StopReader)
            if (plan.stopReader) NfcReaderSession.disable(activity)
            mode = plan.next
        }

    fun enterHce(
        activity: Activity,
        cardId: String,
        cardName: String,
        ndefPayloadBase64: String,
    ): EnterResult =
        synchronized(lock) {
            if (!NfcReaderCapability.isAdapterEnabled(activity)) {
                return EnterResult.NfcOff
            }
            val plan = NfcModePolicy.transition(mode, NfcModeAction.StartHce)
            if (plan.stopReader) NfcReaderSession.disable(activity)
            if (plan.stopHce) stopHceInternal(activity)

            StrawHostApduService.activateNdef(activity, cardId, cardName, ndefPayloadBase64)
            val preferred = setPreferredService(activity)
            if (!preferred) {
                Log.w(TAG, "setPreferredService failed or unsupported; session still active")
            }
            mode = NfcWorkMode.HCE
            EnterResult.Ok
        }

    fun leaveHce(activity: Activity) =
        synchronized(lock) {
            val plan = NfcModePolicy.transition(mode, NfcModeAction.StopHce)
            if (plan.stopHce) stopHceInternal(activity)
            mode = plan.next
        }

    /** Call when leaving Emulate UI or destroying activity. */
    fun releaseAll(activity: Activity) =
        synchronized(lock) {
            NfcReaderSession.disable(activity)
            stopHceInternal(activity)
            mode = NfcWorkMode.IDLE
        }

    private fun stopHceInternal(context: Context) {
        unsetPreferredService(context)
        StrawHostApduService.deactivate(context)
    }

    fun setPreferredService(activity: Activity): Boolean {
        val adapter = NfcReaderCapability.adapterOrNull(activity) ?: return false
        val emulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull() ?: return false
        val component = ComponentName(activity, StrawHostApduService::class.java)
        return runCatching {
            emulation.setPreferredService(activity, component)
            true
        }.getOrDefault(false)
    }

    fun unsetPreferredService(context: Context): Boolean {
        val activity = context as? Activity
        if (activity == null) {
            // Preferred service is Activity-scoped; prefs deactivate is still applied by caller.
            return false
        }
        val adapter = NfcReaderCapability.adapterOrNull(activity) ?: return false
        val emulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull() ?: return false
        return runCatching {
            emulation.unsetPreferredService(activity)
            true
        }.getOrDefault(false)
    }

    fun openNfcSettings(context: Context): Boolean = NfcSettingsIntents.openNfcSettings(context)

    fun adapterEnabled(context: Context): Boolean = NfcReaderCapability.isAdapterEnabled(context)

    fun describeAdapter(context: Context): String {
        val feature = NfcReaderCapability.hasNfcFeature(context)
        val adapter = NfcReaderCapability.adapterOrNull(context)
        val enabled = adapter?.isEnabled == true
        return "mode=$mode feature=$feature adapter=${adapter != null} enabled=$enabled"
    }
}
