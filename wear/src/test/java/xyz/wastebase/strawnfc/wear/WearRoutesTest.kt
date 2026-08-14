package xyz.wastebase.strawnfc.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearRouteTest {
    @Test
    fun detailAndEmulateCarryIds() {
        assertEquals("a1", WearRoute.Detail("a1").id)
        assertEquals("b2", WearRoute.Emulate("b2").id)
    }

    @Test
    fun rootRoutesAreObjects() {
        assertTrue(WearRoute.Consent is WearRoute)
        assertTrue(WearRoute.List is WearRoute)
        assertTrue(WearRoute.Add is WearRoute)
        assertTrue(WearRoute.Backup is WearRoute)
    }
}
