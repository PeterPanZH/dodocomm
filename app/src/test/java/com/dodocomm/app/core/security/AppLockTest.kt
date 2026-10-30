package com.dodocomm.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun `pin is verified against its salted hash`() {
        val (salt, hash) = AppLock.setPin("123456")
        assertTrue(AppLock.verifyPin("123456", salt, hash))
        assertFalse(AppLock.verifyPin("654321", salt, hash))
        assertFalse(AppLock.verifyPin("12345", salt, hash))
    }

    @Test
    fun `same pin gets different salts`() {
        val first = AppLock.setPin("123456")
        val second = AppLock.setPin("123456")
        assertNotEquals(first.first, second.first)
        assertNotEquals(first.second, second.second)
    }

    @Test
    fun `invalid pin format is rejected`() {
        assertFalse(AppLock.isValidPin("12345"))
        assertFalse(AppLock.isValidPin("12345a"))
        assertTrue(AppLock.isValidPin("000000"))
    }
}
