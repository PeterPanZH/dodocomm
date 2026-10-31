package com.dodocomm.app.core.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushChannelTest {

    @Test
    fun `channel config survives JSON round trip`() {
        val channel = PushChannel(
            id = "ntfy-1",
            type = ChannelType.NTFY,
            name = "Phone alerts",
            serverUrl = "https://ntfy.example",
            topic = "sms",
            token = "secret",
            priority = "high",
        )
        val restored = PushChannel.fromJson(channel.toJson())
        assertEquals(channel, restored)
        assertTrue(restored.isConfigured())
    }

    @Test
    fun `required fields are checked per channel type`() {
        assertFalse(PushChannel.create(ChannelType.NTFY).isConfigured())
        assertFalse(PushChannel.create(ChannelType.TELEGRAM).isConfigured())
        assertFalse(PushChannel.create(ChannelType.PUSHOVER).isConfigured())
        assertFalse(PushChannel.create(ChannelType.EMAIL).isConfigured())
        assertFalse(PushChannel.bark().isConfigured())
        assertTrue(PushChannel.bark(serverUrl = "https://api.day.app", deviceKey = "key").isConfigured())
    }
}
