package com.dodocomm.app.core.channel

import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

/** Pure RFC 5322/MIME message builder used by the SMTP client. */
object SmtpMessage {
    private val email = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")

    fun encodeHeader(value: String): String {
        val sanitized = value.replace("\r", " ").replace("\n", " ").trim()
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(sanitized.toByteArray(Charsets.UTF_8)) + "?="
    }

    fun isValidEmail(value: String): Boolean = email.matches(value.trim())

    fun splitRecipients(value: String): List<String> =
        value.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun build(
        from: String,
        recipients: List<String>,
        subject: String,
        body: String,
        now: Long = System.currentTimeMillis(),
    ): String {
        require(recipients.isNotEmpty()) { "没有收件人" }
        require(recipients.all(::isValidEmail)) { "收件人邮箱格式不正确" }
        require(isValidEmail(from)) { "发件邮箱格式不正确" }
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date(now))
        val encodedBody = Base64.getMimeEncoder(76, "\r\n".toByteArray())
            .encodeToString(body.toByteArray(Charsets.UTF_8))
        val header = buildString {
            append("Date: ").append(date).append("\r\n")
            append("From: ").append(from).append("\r\n")
            append("To: ").append(recipients.joinToString(", ")).append("\r\n")
            append("Subject: ").append(encodeHeader(subject)).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
        }
        return header + "\r\n" + encodedBody + "\r\n"
    }

    /** RFC 5321 requires a leading period on lines that start with a period. */
    fun dotStuff(message: String): String =
        message.split("\r\n").joinToString("\r\n") { line ->
            if (line.startsWith(".")) ".$line" else line
        }
}
