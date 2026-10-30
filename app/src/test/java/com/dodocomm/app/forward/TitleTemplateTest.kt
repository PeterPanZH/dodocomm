package com.dodocomm.app.forward

import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for `{sender}` / `{sim}` / `{time}` / `{device}` / `{number}`. */
class TitleTemplateTest {

    private val timestamp = 1_700_000_000_000L

    private fun payload(
        sender: String = "95588",
        simLabel: String? = "SIM 2 · 中国移动",
        deviceName: String? = "Pixel 9",
        receiverNumber: String? = "13800138000",
    ) = SmsPayload(
        sender = sender,
        body = "您的验证码是 123456",
        smsTimestamp = timestamp,
        receivedAt = timestamp + 5_000L,
        subscriptionId = 2,
        simLabel = simLabel,
        deviceName = deviceName,
        receiverNumber = receiverNumber,
    )

    private val clock: String = TimeFormatter.formatClock(timestamp)

    @Test
    fun `blank template falls back to sender`() {
        assertEquals("来自 95588", TitleTemplate.render("", payload()))
        assertEquals("来自 95588", TitleTemplate.render("   ", payload()))
    }

    @Test
    fun `renders all three tokens`() {
        val title = TitleTemplate.render("{sender} · {sim} · {time}", payload())
        assertEquals("95588 · SIM 2 · 中国移动 · $clock", title)
    }

    @Test
    fun `renders device name and receiver number`() {
        val title = TitleTemplate.render("{device} · {number} · {sender}", payload())
        assertEquals("Pixel 9 · 13800138000 · 95588", title)
    }

    @Test
    fun `missing device and number are cleaned up`() {
        val title = TitleTemplate.render(
            "{sender} · {device} · {number} · {time}",
            payload(deviceName = null, receiverNumber = ""),
        )
        assertEquals("95588 · $clock", title)
    }

    @Test
    fun `missing sim removes dangling separator`() {
        assertEquals("95588", TitleTemplate.render("{sender} · {sim}", payload(simLabel = null)))
        assertEquals("95588 · $clock", TitleTemplate.render("{sender} · {sim} · {time}", payload(simLabel = "")))
    }

    @Test
    fun `template with only missing token falls back to sender`() {
        assertEquals("来自 95588", TitleTemplate.render("{sim}", payload(simLabel = null)))
    }

    @Test
    fun `literal template is left untouched`() {
        assertEquals("银行提醒 · 信用卡", TitleTemplate.render("银行提醒 · 信用卡", payload()))
    }

    @Test
    fun `repeated separators are collapsed`() {
        assertEquals("95588 · $clock", TitleTemplate.render("{sender} · {sim} · · {time}", payload(simLabel = null)))
    }

    @Test
    fun `cleanup trims edge separators`() {
        assertEquals("a · b", TitleTemplate.cleanup(" · a · b · "))
        assertEquals("a · b", TitleTemplate.cleanup("a  ·  b"))
    }

    @Test
    fun `render never returns blank`() {
        val title = TitleTemplate.render("{sim}", payload(sender = "", simLabel = null))
        assertTrue(title.isNotBlank())
        assertFalse(title.contains("{"))
    }
}
