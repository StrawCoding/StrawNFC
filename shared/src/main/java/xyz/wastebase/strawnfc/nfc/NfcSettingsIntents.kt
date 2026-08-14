package xyz.wastebase.strawnfc.nfc

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Best-effort deep links to system NFC / wireless settings.
 * App cannot force the radio on; user must toggle in Settings.
 */
object NfcSettingsIntents {
    fun openNfcSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_NFC_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = launch.resolveActivity(context.packageManager) != null
            if (resolved) {
                return runCatching {
                    context.startActivity(launch)
                    true
                }.getOrDefault(false)
            }
        }
        return false
    }
}
