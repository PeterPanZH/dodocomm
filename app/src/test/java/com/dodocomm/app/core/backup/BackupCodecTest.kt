package com.dodocomm.app.core.backup

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val payload = """{"serverUrl":"https://example.com","deviceKey":"secret-key"}"""
    private val password = "correct horse battery"
    private val deviceKey = ByteArray(32) { (it + 1).toByte() }

    private fun wrap(dek: ByteArray): WrappedKey {
        val iv = ByteArray(12) { (it + 7).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(deviceKey, "AES"), GCMParameterSpec(128, iv))
        }
        return WrappedKey(iv, cipher.doFinal(dek))
    }

    private fun unwrap(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(deviceKey, "AES"), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    @Test
    fun `round trips with password`() {
        val backup = BackupCodec.encode(payload, password)
        assertEquals(payload, BackupCodec.decodeWithPassword(backup, password))
        assertTrue(BackupCodec.info(backup).hasDeviceWrapper.not())
    }

    @Test
    fun `round trips with device key wrapper`() {
        val backup = BackupCodec.encode(payload, password) { dek -> wrap(dek) }
        assertTrue(BackupCodec.info(backup).hasDeviceWrapper)
        assertEquals(payload, BackupCodec.decodeWithPassword(backup, password))
        assertEquals(payload, BackupCodec.decodeWithDeviceWrapper(backup, ::unwrap))
    }

    @Test(expected = Exception::class)
    fun `wrong password cannot decrypt`() {
        val backup = BackupCodec.encode(payload, password)
        BackupCodec.decodeWithPassword(backup, "wrong password")
    }

    @Test
    fun `backups are randomized`() {
        val first = BackupCodec.encode(payload, password)
        val second = BackupCodec.encode(payload, password)
        assertNotEquals(first.toList(), second.toList())
    }
}
