package com.dodocomm.app.forward

import com.dodocomm.app.core.model.FilterDecision
import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.settings.AppSettings

/**
 * Pure, synchronous forwarding-rule evaluation.
 *
 * Order matters and is intentionally explicit:
 *  1. Sender blacklist always wins (an explicit block cannot be overridden).
 *  2. A non-empty sender whitelist only lets matching senders through.
 *  3. Keyword blacklist scans the message body (case-insensitive).
 *
 * Phone numbers are compared on digits only, with a suffix rule so that
 * `+86 13800138000` and `13800138000` are treated as the same number.
 * None of this touches Android APIs, so it is covered by plain JVM unit tests.
 */
object FilterRules {

    /** Minimum digit count for the tolerant "+86 country code" suffix match. */
    private const val MIN_SUFFIX_DIGITS = 7

    fun evaluate(settings: AppSettings, payload: SmsPayload): FilterDecision {
        val sender = payload.sender.trim()

        val blacklisted = settings.senderBlacklist.findMatchedEntry(sender)
        if (blacklisted != null) {
            return FilterDecision.filtered("发送方「${sender.displaySender()}」命中黑名单「$blacklisted」")
        }

        // Blank entries (e.g. leftovers in the settings editor) are not rules.
        val whitelist = settings.senderWhitelist.filter { it.isNotBlank() }
        if (whitelist.isNotEmpty() && whitelist.findMatchedEntry(sender) == null) {
            return FilterDecision.filtered("发送方「${sender.displaySender()}」不在白名单中")
        }

        for (rawKeyword in settings.keywordBlacklist) {
            val keyword = rawKeyword.trim()
            if (keyword.isEmpty()) continue
            if (payload.body.contains(keyword, ignoreCase = true)) {
                return FilterDecision.filtered("短信内容命中关键词「$keyword」")
            }
        }

        return FilterDecision.Pass
    }

    /** @return the first configured entry that matches [sender], or null. */
    private fun List<String>.findMatchedEntry(sender: String): String? =
        firstOrNull { entry -> entry.trim().let { it.isNotEmpty() && it.matchesSender(sender) } }

    /**
     * A rule entry matches when either
     *  - the literal strings are equal (case-insensitive, e.g. sender IDs), or
     *  - their digit-only forms are equal / one is a >=7-digit suffix of the other.
     */
    internal fun String.matchesSender(sender: String): Boolean {
        val entry = trim()
        val target = sender.trim()
        if (entry.isEmpty() || target.isEmpty()) return false
        if (entry.equals(target, ignoreCase = true)) return true

        val entryDigits = entry.digitsOnly()
        val senderDigits = target.digitsOnly()
        if (entryDigits.isEmpty() || senderDigits.isEmpty()) return false
        if (entryDigits == senderDigits) return true

        val shorter = if (entryDigits.length <= senderDigits.length) entryDigits else senderDigits
        val longer = if (entryDigits.length > senderDigits.length) entryDigits else senderDigits
        return shorter.length >= MIN_SUFFIX_DIGITS && longer.endsWith(shorter)
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }

    private fun String.displaySender(): String = ifBlank { "未知号码" }
}
