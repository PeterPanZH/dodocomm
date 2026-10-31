package com.dodocomm.app.forward

import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.util.OtpExtractor
import com.dodocomm.app.core.util.TimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure Bark payload rendering for the notification title and body.
 *
 * Tokens: `{sender}`, `{sim}`, `{time}` (HH:mm), `{date}` (yyyy-MM-dd),
 * `{datetime}` (yyyy-MM-dd HH:mm), `{device}` (phone name), `{number}` (the
 * receiving SIM's phone number). Body templates additionally support `{body}`
 * (the original SMS text).
 *
 * Empty tokens are removed together with the separators they leave behind, so
 * `"{device} · {sim}"` degrades to `"{device}"` instead of ending with " · ".
 */
object TitleTemplate {

    const val DEFAULT_TEMPLATE: String = AppSettings.DEFAULT_TITLE_TEMPLATE
    const val DEFAULT_BODY_TEMPLATE: String = AppSettings.DEFAULT_BODY_TEMPLATE

    private const val TOKEN_SENDER = "{sender}"
    private const val TOKEN_SIM = "{sim}"
    private const val TOKEN_TIME = "{time}"
    private const val TOKEN_DATE = "{date}"
    private const val TOKEN_DATETIME = "{datetime}"
    private const val TOKEN_DEVICE = "{device}"
    private const val TOKEN_NUMBER = "{number}"
    private const val TOKEN_BODY = "{body}"
    private const val TOKEN_CODE = "{code}"
    private const val FALLBACK_TITLE = "新短信"
    private const val BODY_SENTINEL = "\u0000BODY\u0000"

    private val REPEATED_SEPARATOR = Regex("(?:\\s*·\\s*){2,}")
    private val EDGE_SEPARATOR = Regex("^[\\s·]+|[\\s·]+$")
    private val MULTIPLE_SPACES = Regex(" {2,}")

    fun render(template: String, payload: SmsPayload): String {
        val source = template.ifBlank { DEFAULT_TEMPLATE }
        val rendered = cleanup(replaceTokens(source, payload))
        if (rendered.isNotEmpty()) return rendered

        // The template only contained tokens that resolved to nothing; fall back
        // to the default so the Bark title is never empty.
        val fallback = cleanup(replaceTokens(DEFAULT_TEMPLATE, payload))
        return fallback.ifEmpty { FALLBACK_TITLE }
    }

    /**
     * Renders the Bark body. The original SMS is always preserved: when the
     * template does not contain `{body}`, the rendered metadata is appended
     * after the message instead of replacing it.
     */
    fun renderBody(template: String, payload: SmsPayload): String {
        val source = template.ifBlank { DEFAULT_BODY_TEMPLATE }
        val hasBodyToken = source.contains(TOKEN_BODY)
        val prepared = if (hasBodyToken) source.replace(TOKEN_BODY, BODY_SENTINEL) else source
        val rendered = cleanup(replaceTokens(prepared, payload))

        if (hasBodyToken) {
            return rendered.replace(BODY_SENTINEL, payload.body).trim('\n')
        }
        val metadata = rendered.replace(BODY_SENTINEL, "").trim()
        return if (metadata.isEmpty()) payload.body else payload.body.trimEnd() + "\n\n" + metadata
    }

    private fun replaceTokens(source: String, payload: SmsPayload): String {
        val timestamp = if (payload.smsTimestamp > 0L) payload.smsTimestamp else payload.receivedAt
        val zoned = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val date = zoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val datetime = zoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return source
            .replace(TOKEN_SENDER, payload.sender.trim())
            .replace(TOKEN_SIM, payload.simLabel?.trim().orEmpty())
            .replace(TOKEN_TIME, TimeFormatter.formatClock(timestamp))
            .replace(TOKEN_DATE, date)
            .replace(TOKEN_DATETIME, datetime)
            .replace(TOKEN_DEVICE, payload.deviceName?.trim().orEmpty())
            .replace(TOKEN_NUMBER, payload.receiverNumber?.trim().orEmpty())
            .replace(TOKEN_CODE, OtpExtractor.extract(payload.body).orEmpty())
            .replace(TOKEN_BODY, payload.body)
    }

    /** Collapses separator runs left by empty tokens and trims edges. */
    internal fun cleanup(raw: String): String = raw
        .replace(REPEATED_SEPARATOR, " · ")
        .replace(MULTIPLE_SPACES, " ")
        .replace(EDGE_SEPARATOR, "")
        .trim()
}
