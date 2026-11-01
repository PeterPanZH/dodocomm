package com.dodocomm.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardSourceTest {
    @Test
    fun `source storage is backward compatible`() {
        assertEquals(ForwardSource.SMS, ForwardSource.fromStorage(null))
        assertEquals(ForwardSource.SMS, ForwardSource.fromStorage("unknown"))
        assertEquals(ForwardSource.CALL, ForwardSource.fromStorage("CALL"))
        assertEquals(ForwardSource.APP_NOTIFICATION, ForwardSource.fromStorage("APP_NOTIFICATION"))
    }

    @Test
    fun `record defaults to sms`() {
        val record = ForwardRecord(sender = "10086", body = "test", receivedAt = 1L, createdAt = 1L)
        assertEquals(ForwardSource.SMS, record.source)
    }
}
