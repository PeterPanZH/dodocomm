package com.dodocomm.app.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val payload = """{"serverUrl":"https://example.com","deviceKey":"secret-key"}"""
    private val password = "correct horse battery"
    private val deviceKey = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `round trips with password`() {
        val backup = BackupCodec.encode(payload, password, deviceKey = null)
        assertEquals(payload, BackupCodec.decodeWithPassword(backup, password))
        assertTrue(BackupCodec.info(backup).hasDeviceWrapper.not())
    }

    @Test
    fun `round trips with device key wrapper`() {
        val backup = BackupCodec.encode(payload, password, deviceKey)
        assertTrue(BackupCodec.info(backup).hasDeviceWrapper)
        assertEquals(payload, BackupCodec.decodeWithPassword(backup, password))
        assertEquals(payload, BackupCodec.decodeWithDeviceKey(backup, deviceKey))
    }

    @Test(expected = Exception::class)
    fun `wrong password cannot decrypt`() {
        val backup = BackupCodec.encode(payload, password, deviceKey = null)
        BackupCodec.decodeWithPassword(backup, "wrong password")
    }

    @Test
    fun `backups are randomized`() {
        val first = BackupCodec.encode(payload, password, deviceKey = null)
        val second = BackupCodec.encode(payload, password, deviceKey = null)
        assertNotEquals(first.toList(), second.toList())
    }
}
