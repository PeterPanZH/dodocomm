package com.dodocomm.app.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PushLevelTest {
    @Test
    fun `storage accepts enum names and api values`() {
        assertEquals(PushLevel.PASSIVE, PushLevel.fromStorage("PASSIVE"))
        assertEquals(PushLevel.PASSIVE, PushLevel.fromStorage("passive"))
        assertEquals(PushLevel.CRITICAL, PushLevel.fromStorage("critical"))
        assertEquals(PushLevel.ACTIVE, PushLevel.fromStorage("unknown"))
    }
}
