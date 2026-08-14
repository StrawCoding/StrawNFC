package xyz.wastebase.strawnfc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPathsTest {

    @Test
    fun cardPath_buildsCanonicalShape() {
        assertEquals("/strawnfc/cards/abc-123", SyncPaths.cardPath("abc-123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cardPath_rejectsBlankId() {
        SyncPaths.cardPath("  ")
    }

    @Test
    fun isCardPath_acceptsLeafIdsOnly() {
        assertTrue(SyncPaths.isCardPath("/strawnfc/cards/uuid-1"))
        assertFalse(SyncPaths.isCardPath("/strawnfc/cards/"))
        assertFalse(SyncPaths.isCardPath("/strawnfc/cards/a/b"))
        assertFalse(SyncPaths.isCardPath("/other/cards/x"))
        assertFalse(SyncPaths.isCardPath(null))
    }

    @Test
    fun cardIdFromPath_extractsId() {
        assertEquals("uuid-1", SyncPaths.cardIdFromPath("/strawnfc/cards/uuid-1"))
        assertNull(SyncPaths.cardIdFromPath("/strawnfc/cards/"))
    }
}
