package xyz.wastebase.strawnfc.hce

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Base64
import android.util.Log
import xyz.wastebase.strawnfc.hce.Type4NdefApduHandler as SharedType4

/**
 * Stock HCE service: NFC Forum Type 4 Tag APDU only.
 * Does **not** spoof UID / Classic / DESFire. No "door unlocked" claims.
 */
class StrawHostApduService : HostApduService() {
    private var handler: SharedType4? = null
    private var loadedEpoch: Long = NO_EPOCH

    override fun onCreate() {
        super.onCreate()
        val loaded = reloadHandler()
        Log.d(ApduLog.TAG, "service onCreate handler=${loaded != null} ${describeSession()}")
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: run {
            Log.w(ApduLog.TAG, "RX: null APDU")
            record("RX null")
            return SharedType4.SW_WRONG_LENGTH
        }
        Log.d(ApduLog.TAG, ApduLog.lineRx(apdu))
        record(ApduLog.lineRx(apdu))
        val active = activeHandler()
        val response = active?.process(apdu) ?: SharedType4.SW_CONDITIONS_NOT_SATISFIED
        if (active == null) {
            Log.w(ApduLog.TAG, "TX without Type4 handler (session inactive or payload missing)")
        }
        Log.d(ApduLog.TAG, ApduLog.lineTx(response))
        record(ApduLog.lineTx(response))
        return response
    }

    override fun onDeactivated(reason: Int) {
        Log.d(ApduLog.TAG, "deactivated reason=$reason (0=deactivated, 1=link loss)")
        record("DEACT reason=$reason")
        // Drop selection + cached payload so the next RF session must re-read the
        // current session state (stopped session must never keep answering).
        handler?.reset()
        handler = null
        loadedEpoch = NO_EPOCH
    }

    /**
     * The service instance outlives a session, so a cached handler must be
     * invalidated whenever start/stop bumped the session epoch.
     */
    private fun activeHandler(): SharedType4? {
        val epoch = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_EPOCH, NO_EPOCH)
        if (handler == null || epoch != loadedEpoch) {
            reloadHandler()
        }
        return handler
    }

    private fun describeSession(): String {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return "active=${prefs.getBoolean(KEY_ACTIVE, false)} card=${prefs.getString(KEY_CARD_ID, null)}"
    }

    private fun reloadHandler(): SharedType4? {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val b64 = prefs.getString(KEY_NDEF_B64, null)
        loadedEpoch = prefs.getLong(KEY_EPOCH, NO_EPOCH)
        handler = if (active && !b64.isNullOrBlank()) {
            runCatching {
                SharedType4(Base64.decode(b64, Base64.DEFAULT))
            }.onFailure { t ->
                Log.e(ApduLog.TAG, "Type4 handler decode failed", t)
            }.getOrNull()
        } else {
            null
        }
        return handler
    }

    companion object {
        const val PREFS = "strawnfc_hce_session"
        const val KEY_ACTIVE = "active"
        const val KEY_NDEF_B64 = "ndef_b64"
        const val KEY_CARD_ID = "card_id"
        const val KEY_CARD_NAME = "card_name"
        const val KEY_EPOCH = "session_epoch"
        const val NO_EPOCH = 0L
        private const val MAX_APDU_LOG = 12

        private val apduLines = ArrayDeque<String>(MAX_APDU_LOG)

        fun activateNdef(context: Context, cardId: String, cardName: String, ndefPayloadBase64: String) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ok = prefs
                .edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_CARD_ID, cardId)
                .putString(KEY_CARD_NAME, cardName)
                .putString(KEY_NDEF_B64, ndefPayloadBase64)
                .putLong(KEY_EPOCH, nextEpoch(prefs))
                .commit()
            clearApduLog()
            record("SESSION start card=$cardId commit=$ok")
            Log.d(ApduLog.TAG, "activateNdef card=$cardId commit=$ok")
        }

        fun deactivate(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs
                .edit()
                .putBoolean(KEY_ACTIVE, false)
                .remove(KEY_NDEF_B64)
                .remove(KEY_CARD_ID)
                .remove(KEY_CARD_NAME)
                .putLong(KEY_EPOCH, nextEpoch(prefs))
                .commit()
            record("SESSION stop")
            Log.d(ApduLog.TAG, "deactivate")
        }

        /**
         * Strictly increasing so a stop+start inside the same millisecond still
         * invalidates a cached handler.
         */
        private fun nextEpoch(prefs: android.content.SharedPreferences): Long {
            val previous = prefs.getLong(KEY_EPOCH, NO_EPOCH)
            return maxOf(System.currentTimeMillis(), previous + 1)
        }

        fun isActive(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)

        fun activeCardId(context: Context): String? =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CARD_ID, null)

        fun snapshotApduLog(): List<String> = synchronized(apduLines) { apduLines.toList() }

        fun clearApduLog() {
            synchronized(apduLines) { apduLines.clear() }
        }

        internal fun record(line: String) {
            synchronized(apduLines) {
                if (apduLines.size >= MAX_APDU_LOG) apduLines.removeFirst()
                apduLines.addLast(line)
            }
        }
    }
}
