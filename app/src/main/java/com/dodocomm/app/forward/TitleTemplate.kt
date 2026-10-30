package com.dodocomm.app.forward

import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.util.TimeFormatter

/**
 * Pure title rendering for the Bark notification title.
 *
 * Supported tokens: `{sender}`, `{sim}`, `{time}` (HH:mm), `{device}` (phone
 * name) and `{number}` (the receiving SIM's phone number). A blank template
 * falls back to "来自 {sender}". Tokens that resolve to an empty value (for
 * example `{sim}` when READ_PHONE_STATE is not granted) are removed together
 * with the separators they leave behind, so `"{sender} · {sim}"` degrades to
 * `"{sender}"` instead of ending with a dangling " · ".
 */
object TitleTemplate {

    const val DEFAULT_TEMPLATE: String = "来自 {sender}"

    private const val TOKEN_SENDER = "{sender}"
    private const val TOKEN_SIM = "{sim}"
    private const val TOKEN_TIME = "{time}"
    private const val TOKEN_DEVICE = "{device}"
    private const val TOKEN_NUMBER = "{number}"
    private const val FALLBACK_TITLE = "新短信"

    private val REPEATED_SEPARATOR = Regex("(?:\\s*·\\s*){2,}")
    private val EDGE_SEPARATOR = Regex("^[\\s·]+|[\\s·]+$")
    private val MULTIPLE_SPACES = Regex(" {2,}")

    fun render(template: String, payload: SmsPayload): String {
        val source = template.ifBlank { DEFAULT_TEMPLATE }
        val sender = payload.sender.trim()
        val simLabel = payload.simLabel?.trim().orEmpty()
        val clock = TimeFormatter.formatClock(
            if (payload.smsTimestamp > 0L) payload.smsTimestamp else payload.receivedAt,
        )
        val device = payload.deviceName?.trim().orEmpty()
        val receiverNumber = payload.receiverNumber?.trim().orEmpty()

        val rendered = cleanup(
            source
                .replace(TOKEN_SENDER, sender)
                .replace(TOKEN_SIM, simLabel)
                .replace(TOKEN_TIME, clock)
                .replace(TOKEN_DEVICE, device)
                .replace(TOKEN_NUMBER, receiverNumber),
        )
        if (rendered.isNotEmpty()) return rendered

        // The template only contained tokens that resolved to nothing; fall back
        // to the default so the Bark title is never empty.
        val fallback = cleanup(DEFAULT_TEMPLATE.replace(TOKEN_SENDER, sender))
        return fallback.ifEmpty { FALLBACK_TITLE }
    }

    /** Collapses separator runs left by empty tokens and trims edges. */
    internal fun cleanup(raw: String): String = raw
        .replace(REPEATED_SEPARATOR, " · ")
        .replace(MULTIPLE_SPACES, " ")
        .replace(EDGE_SEPARATOR, "")
        .trim()
}
