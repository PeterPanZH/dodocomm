package com.dodocomm.app.core.routing

import com.dodocomm.app.core.channel.ChannelType
import com.dodocomm.app.core.channel.PushChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingEngineTest {

    private val bark = PushChannel.bark()
    private val ntfy = PushChannel(id = "ntfy-1", type = ChannelType.NTFY, name = "ntfy")

    @Test
    fun `no rules sends to all active channels`() {
        assertEquals(listOf(bark, ntfy), RoutingEngine.targets(ctx(), emptyList(), listOf(bark, ntfy)))
    }

    @Test
    fun `sender keyword and time conditions are ANDed`() {
        val rule = RoutingRule(
            id = "r1",
            name = "Bank",
            senderContains = "95588",
            keywordContains = "验证码",
            timeStartMinutes = 8 * 60,
            timeEndMinutes = 22 * 60,
            targetChannelIds = listOf("ntfy-1"),
        )
        val targets = RoutingEngine.targets(ctx(now = 9 * 60), listOf(rule), listOf(bark, ntfy))
        assertEquals(listOf(ntfy), targets)
        assertEquals(listOf(bark, ntfy), RoutingEngine.targets(ctx(now = 23 * 60), listOf(rule), listOf(bark, ntfy)))
    }

    @Test
    fun `invalid regex never crashes or matches`() {
        val rule = RoutingRule(id = "bad", name = "bad", senderRegex = "[", targetChannelIds = listOf("ntfy-1"))
        assertEquals(listOf(bark, ntfy), RoutingEngine.targets(ctx(), listOf(rule), listOf(bark, ntfy)))
    }

    @Test
    fun `first matching rule wins`() {
        val first = RoutingRule(id = "1", name = "first", senderContains = "95588", targetChannelIds = listOf("ntfy-1"))
        val second = RoutingRule(id = "2", name = "second", keywordContains = "验证码", targetChannelIds = listOf("bark"))
        assertEquals(listOf(ntfy), RoutingEngine.targets(ctx(), listOf(first, second), listOf(bark, ntfy)))
    }

    private fun ctx(now: Int = 12 * 60) = RoutingContext(
        sender = "95588",
        body = "您的验证码是 123456",
        simLabel = "SIM 2 · 中国移动",
        nowMinutes = now,
    )
}
