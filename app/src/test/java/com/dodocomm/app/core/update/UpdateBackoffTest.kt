package com.dodocomm.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateBackoffTest {

    private val hour = 60L * 60 * 1000

    @Test
    fun `backoff doubles and caps at one day`() {
        assertEquals(hour, UpdatePrefs.backoffDelay(1))
        assertEquals(2 * hour, UpdatePrefs.backoffDelay(2))
        assertEquals(4 * hour, UpdatePrefs.backoffDelay(3))
        assertEquals(8 * hour, UpdatePrefs.backoffDelay(4))
        assertEquals(16 * hour, UpdatePrefs.backoffDelay(5))
        assertEquals(UpdatePrefs.MAX_BACKOFF_MS, UpdatePrefs.backoffDelay(6))
        assertEquals(UpdatePrefs.MAX_BACKOFF_MS, UpdatePrefs.backoffDelay(50))
    }

    @Test
    fun `zero failures means no delay`() {
        assertEquals(0L, UpdatePrefs.backoffDelay(0))
    }
}
