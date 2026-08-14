package xyz.wastebase.strawnfc.hce

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Base64
import xyz.wastebase.strawnfc.hce.Type4NdefApduHandler as SharedType4

/**
 * Stock HCE service for NDEF Type 4 only.
 * Does **not** spoof UID / Classic / DESFire. No "door unlocked" claims.
 */
class StrawHostApduService : HostApduService() {
    private var handler: SharedType4? = null

    override fun onCreate() {
        super.onCreate()
        reloadHandler()
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SharedType4.SW_WRONG_LENGTH
        val active = handler ?: reloadHandler()
        return active?.process(apdu) ?: SharedType4.SW_CONDITIONS_NOT_SATISFIED
    }

    override fun onDeactivated(reason: Int) {
        // Keep handler; session ends without claiming access granted.
    }

    private fun reloadHandler(): SharedType4? {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val b64 = prefs.getString(KEY_NDEF_B64, null)
        handler = if (active && !b64.isNullOrBlank()) {
            runCatching {
                SharedType4(Base64.decode(b64, Base64.DEFAULT))
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

        fun activateNdef(context: Context, cardId: String, cardName: String, ndefPayloadBase64: String) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_CARD_ID, cardId)
                .putString(KEY_CARD_NAME, cardName)
                .putString(KEY_NDEF_B64, ndefPayloadBase64)
                .apply()
        }

        fun deactivate(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, false)
                .remove(KEY_NDEF_B64)
                .remove(KEY_CARD_ID)
                .remove(KEY_CARD_NAME)
                .apply()
        }

        fun isActive(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)

        fun activeCardId(context: Context): String? =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CARD_ID, null)
    }
}
