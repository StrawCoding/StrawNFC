package xyz.wastebase.strawnfc.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearRoutesTest {
    @Test
    fun detailAndEmulatePathsEncodeCardId() {
        assertEquals("detail/abc-123", WearRoutes.detail("abc-123"))
        assertEquals("emulate/abc-123", WearRoutes.emulate("abc-123"))
        assertTrue(WearRoutes.DETAIL.contains("{cardId}"))
        assertTrue(WearRoutes.EMULATE.contains("{cardId}"))
    }

    @Test
    fun rootRoutesAreStable() {
        assertEquals("consent", WearRoutes.CONSENT)
        assertEquals("list", WearRoutes.LIST)
        assertEquals("add", WearRoutes.ADD)
        assertEquals("backup", WearRoutes.BACKUP)
    }
}
