package com.dodocomm.app.core.forward

import com.dodocomm.app.core.channel.ChannelSendResult
import com.dodocomm.app.core.channel.ChannelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPolicyTest {
    private fun result(name: String, success: Boolean) =
        ChannelSendResult(name, name, ChannelType.NTFY, success, if (success) "ok" else "failed")

    @Test
    fun `any successful channel means sent without retry`() {
        val outcome = DeliveryPolicy.evaluate(listOf(result("bark", true), result("telegram", false)))
        assertTrue(outcome.sent)
        assertFalse(outcome.retry)
        assertTrue(outcome.error.orEmpty().contains("telegram"))
    }

    @Test
    fun `all failures retry`() {
        val outcome = DeliveryPolicy.evaluate(listOf(result("bark", false), result("telegram", false)))
        assertFalse(outcome.sent)
        assertTrue(outcome.retry)
        assertEquals(2, outcome.error.orEmpty().split("；").size)
    }

    @Test
    fun `empty channel list asks for retry`() {
        assertTrue(DeliveryPolicy.evaluate(emptyList()).retry)
    }
}
