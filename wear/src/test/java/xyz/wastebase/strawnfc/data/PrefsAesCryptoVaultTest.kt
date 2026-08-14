package xyz.wastebase.strawnfc.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class PrefsAesCryptoVaultTest {
    @Test
    fun roundTripEncryptDecrypt() {
        val store = ConcurrentHashMap<String, ByteArray>()
        val vault = PrefsAesCryptoVault {
            store.getOrPut("k") {
                ByteArray(32) { it.toByte() }
            }
        }
        val plain = "strawnfc-xiaomi".toByteArray()
        val cipher = vault.encrypt(plain)
        assertFalse(cipher.contentEquals(plain))
        assertArrayEquals(plain, vault.decrypt(cipher))
    }
}
