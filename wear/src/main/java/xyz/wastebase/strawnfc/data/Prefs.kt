package xyz.wastebase.strawnfc.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Wear preferences — consent gate and related flags.
 */
class Prefs(
    private val prefs: SharedPreferences,
) {
    var consentAccepted: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_CONSENT, value).apply()

    /** Last encrypted `.strawnfc` blob as Base64 (Wear local re-import; phone uses SAF). */
    var lastBackupBase64: String?
        get() = prefs.getString(KEY_LAST_BACKUP, null)
        set(value) = prefs.edit().putString(KEY_LAST_BACKUP, value).apply()

    fun acceptConsent() {
        consentAccepted = true
    }

    companion object {
        private const val PREFS_NAME = "strawnfc_prefs"
        private const val KEY_CONSENT = "consent_accepted_v1"
        private const val KEY_LAST_BACKUP = "last_backup_b64_v1"

        fun create(context: Context): Prefs =
            Prefs(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}
