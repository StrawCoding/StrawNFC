package xyz.wastebase.strawnfc.nfc

import org.junit.Assert.assertTrue
import org.junit.Test

class NfcReaderSessionTest {
    @Test
    fun defaultFlags_includeNfcA() {
        assertTrue(NfcReaderSession.DEFAULT_FLAGS and android.nfc.NfcAdapter.FLAG_READER_NFC_A != 0)
    }
}
