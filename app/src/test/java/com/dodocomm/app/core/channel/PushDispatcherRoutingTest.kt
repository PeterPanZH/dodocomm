package com.dodocomm.app.core.channel

import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.routing.RoutingContext
import com.dodocomm.app.core.routing.RoutingRule
import com.dodocomm.app.core.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDispatcherRoutingTest {
    private val ntfy = PushChannel(
        id = "ntfy", type = ChannelType.NTFY, name = "ntfy", serverUrl = "https://ntfy.sh", topic = "sms",
    )
    private val telegram = PushChannel(
        id = "tg", type = ChannelType.TELEGRAM, name = "telegram", botToken = "bot", chatId = "chat",
    )

    @Test
    fun `dispatch fan out and partial result are reported`() = runBlocking {
        val dispatcher = PushDispatcher(
            barkClient = BarkClient(),
            senderOverride = { channel, _, _ ->
                ChannelSendResult(channel.id, channel.name, channel.type, channel.id == "ntfy", "test")
            },
        )
        val result = dispatcher.dispatch(
            settings = AppSettings(channels = listOf(ntfy, telegram)),
            title = "t",
            body = "b",
        )
        assertEquals(2, result.results.size)
        assertTrue(result.results.any { it.success })
        assertTrue(result.results.any { !it.success })
    }

    @Test
    fun `routing selects only target channels`() = runBlocking {
        val routed = ntfy
        val dispatcher = PushDispatcher(
            barkClient = BarkClient(),
            senderOverride = { channel, _, _ ->
                ChannelSendResult(channel.id, channel.name, channel.type, true, "ok")
            },
        )
        val result = dispatcher.dispatch(
            settings = AppSettings(
                channels = listOf(ntfy, telegram),
                routingRules = listOf(
                    RoutingRule(id = "r", name = "r", keywordContains = "银行", targetChannelIds = listOf("ntfy")),
                ),
            ),
            title = "t",
            body = "银行验证码",
            routing = RoutingContext(sender = "95588", body = "银行验证码", simLabel = null, nowMinutes = 600),
        )
        assertEquals(1, result.results.size)
        assertEquals(routed.id, result.results.first().channelId)
    }
}
