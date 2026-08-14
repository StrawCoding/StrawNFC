package xyz.wastebase.strawnfc.nfc

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Handler
import android.os.Looper

/**
 * Wear / phone NFC **reader** availability (distinct from HCE emulate).
 * Many watches advertise FEATURE_NFC for payment/HCE but reject ReaderMode — UI must stay honest.
 */
object NfcReaderCapability {
    fun hasNfcFeature(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

    fun adapterOrNull(context: Context): NfcAdapter? =
        runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()

    /** Adapter exists; may still be disabled in system settings. */
    fun isReaderHardwarePresent(context: Context): Boolean =
        hasNfcFeature(context) && adapterOrNull(context) != null

    fun isAdapterEnabled(context: Context): Boolean =
        adapterOrNull(context)?.isEnabled == true
}

/**
 * Foreground ReaderMode helper for Wear Add-card (and optional phone use).
 * Callbacks are posted to the main looper.
 */
object NfcReaderSession {
    val DEFAULT_FLAGS: Int =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

    fun enable(
        activity: Activity,
        flags: Int = DEFAULT_FLAGS,
        onTag: (Tag) -> Unit,
    ): Boolean {
        val adapter = NfcReaderCapability.adapterOrNull(activity) ?: return false
        val main = Handler(Looper.getMainLooper())
        return runCatching {
            adapter.enableReaderMode(
                activity,
                { tag -> main.post { onTag(tag) } },
                flags,
                null,
            )
            true
        }.getOrDefault(false)
    }

    fun disable(activity: Activity) {
        val adapter = NfcReaderCapability.adapterOrNull(activity) ?: return
        runCatching { adapter.disableReaderMode(activity) }
    }
}
