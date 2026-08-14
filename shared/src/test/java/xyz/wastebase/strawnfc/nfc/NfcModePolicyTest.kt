package xyz.wastebase.strawnfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcModePolicyTest {
    @Test
    fun startReader_whileHce_stopsHce() {
        val t = NfcModePolicy.transition(NfcWorkMode.HCE, NfcModeAction.StartReader)
        assertEquals(NfcWorkMode.READER, t.next)
        assertTrue(t.stopHce)
        assertFalse(t.stopReader)
    }

    @Test
    fun startHce_whileReader_stopsReader() {
        val t = NfcModePolicy.transition(NfcWorkMode.READER, NfcModeAction.StartHce)
        assertEquals(NfcWorkMode.HCE, t.next)
        assertTrue(t.stopReader)
        assertFalse(t.stopHce)
    }

    @Test
    fun stopReader_fromReader_goesIdle() {
        val t = NfcModePolicy.transition(NfcWorkMode.READER, NfcModeAction.StopReader)
        assertEquals(NfcWorkMode.IDLE, t.next)
        assertTrue(t.stopReader)
    }

    @Test
    fun stopHce_fromIdle_noop() {
        val t = NfcModePolicy.transition(NfcWorkMode.IDLE, NfcModeAction.StopHce)
        assertEquals(NfcWorkMode.IDLE, t.next)
        assertFalse(t.stopHce)
    }
}
