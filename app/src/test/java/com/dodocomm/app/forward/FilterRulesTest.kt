package com.dodocomm.app.forward

import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the forwarding rules (no Android dependencies). */
class FilterRulesTest {

    private fun payload(sender: String = "95588", body: String = "【工商银行】您尾号 8888 的储蓄卡消费 128.00 元") =
        SmsPayload(
            sender = sender,
            body = body,
            smsTimestamp = 1_700_000_000_000L,
            receivedAt = 1_700_000_000_500L,
            subscriptionId = 1,
            simLabel = "SIM 1 · 中国移动",
        )

    @Test
    fun `default settings forward everything`() {
        val decision = FilterRules.evaluate(AppSettings(), payload())
        assertTrue(decision.forward)
        assertNull(decision.reason)
    }

    @Test
    fun `blacklisted sender is filtered with chinese reason`() {
        val settings = AppSettings(senderBlacklist = listOf("95588"))
        val decision = FilterRules.evaluate(settings, payload())
        assertFalse(decision.forward)
        val reason = checkNotNull(decision.reason)
        assertTrue(reason.contains("黑名单"))
        assertTrue(reason.contains("95588"))
    }

    @Test
    fun `blacklist tolerates plus 86 country code`() {
        val settings = AppSettings(senderBlacklist = listOf("13800138000"))
        val decision = FilterRules.evaluate(settings, payload(sender = "+86 138 0013 8000"))
        assertFalse(decision.forward)
    }

    @Test
    fun `whitelist rejects sender that does not match`() {
        val settings = AppSettings(senderWhitelist = listOf("10086", "95588"))
        val decision = FilterRules.evaluate(settings, payload(sender = "106901234567"))
        assertFalse(decision.forward)
        assertTrue(checkNotNull(decision.reason).contains("白名单"))
    }

    @Test
    fun `whitelist accepts sender that matches with country code`() {
        val settings = AppSettings(senderWhitelist = listOf("13800138000"))
        val decision = FilterRules.evaluate(settings, payload(sender = "+8613800138000"))
        assertTrue(decision.forward)
    }

    @Test
    fun `whitelist accepts alphanumeric sender exactly`() {
        val settings = AppSettings(senderWhitelist = listOf("BANK-ALERT"))
        val decision = FilterRules.evaluate(settings, payload(sender = "bank-alert"))
        assertTrue(decision.forward)
    }

    @Test
    fun `short numbers only match exactly, no suffix trick`() {
        // "138" is below the 7-digit suffix threshold and must not match a full phone number.
        val whitelist = AppSettings(senderWhitelist = listOf("138"))
        assertFalse(FilterRules.evaluate(whitelist, payload(sender = "13800138000")).forward)

        val blacklist = AppSettings(senderBlacklist = listOf("10086"))
        assertTrue(FilterRules.evaluate(blacklist, payload(sender = "1008611")).forward)
    }

    @Test
    fun `blacklist wins over whitelist`() {
        val settings = AppSettings(
            senderWhitelist = listOf("95588"),
            senderBlacklist = listOf("95588"),
        )
        assertFalse(FilterRules.evaluate(settings, payload()).forward)
    }

    @Test
    fun `keyword blacklist is case insensitive and explains itself`() {
        val settings = AppSettings(keywordBlacklist = listOf("退订", "unsubscribe"))
        val chinese = FilterRules.evaluate(
            settings,
            payload(body = "【推广】点击链接立减 100 元，退订回 T。"),
        )
        assertFalse(chinese.forward)
        val chineseReason = checkNotNull(chinese.reason)
        assertTrue(chineseReason.contains("关键词"))
        assertTrue(chineseReason.contains("退订"))

        val english = FilterRules.evaluate(
            settings,
            payload(body = "Reply to UNSUBSCRIBE if you don't want more messages"),
        )
        assertFalse(english.forward)
    }

    @Test
    fun `blank keyword and sender entries are ignored`() {
        val settings = AppSettings(
            senderWhitelist = listOf(" ", ""),
            senderBlacklist = listOf(""),
            keywordBlacklist = listOf("  ", ""),
        )
        // Blank editor leftovers must not turn the whitelist into "reject all".
        val decision = FilterRules.evaluate(settings, payload())
        assertTrue(decision.forward)
    }

    @Test
    fun `identical digit formats match`() {
        val settings = AppSettings(senderBlacklist = listOf("+86 138-0013-8000"))
        val decision = FilterRules.evaluate(settings, payload(sender = "13800138000"))
        assertFalse(decision.forward)
    }

    @Test
    fun `matches 7 digit suffix of a longer number`() {
        // sender digits: 861234567890 -> last 7 digits "4567890"
        val settings = AppSettings(senderBlacklist = listOf("4567890"))
        val decision = FilterRules.evaluate(settings, payload(sender = "+86 123 4567 890"))
        assertFalse(decision.forward)
    }
}
