package com.dodocomm.app.core.util

/**
 * Best-effort extraction of a one-time code from an SMS body.
 *
 * Deliberately conservative: only codes that appear near a known keyword are
 * extracted, so ordinary 4–8 digit numbers (amounts, order numbers) are not
 * mistaken for verification codes.
 */
object OtpExtractor {

    private val KEYWORD_CODE = Regex(
        "(?:验证码|校验码|动态码|确认码|安全码|短信码|登录码|verification\\s*code|code|otp)" +
            "[^0-9A-Za-z]{0,12}([0-9]{4,8}|[A-Z0-9]{4,8})",
        RegexOption.IGNORE_CASE,
    )

    /** A 4–8 digit/alphanumeric code immediately followed by 验证码-like wording. */
    private val CODE_THEN_KEYWORD = Regex(
        "([0-9]{4,8})[^0-9]{0,6}(?:验证码|校验码|动态码|确认码|安全码)",
    )

    fun extract(body: String): String? {
        if (body.isBlank()) return null
        KEYWORD_CODE.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            if (candidate.any { it.isDigit() }) return normalize(candidate)
        }
        CODE_THEN_KEYWORD.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            if (candidate.any { it.isDigit() }) return normalize(candidate)
        }
        return null
    }

    private fun normalize(raw: String): String = raw.trim().uppercase()
}
