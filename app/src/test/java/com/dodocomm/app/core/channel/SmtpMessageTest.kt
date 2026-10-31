package com.dodocomm.app.core.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpMessageTest {
    @Test
    fun `validates recipient addresses`() {
        assertTrue(SmtpMessage.isValidEmail("you@example.com"))
        assertFalse(SmtpMessage.isValidEmail("not-an-email"))
        assertEquals(listOf("a@example.com", "b@example.com"), SmtpMessage.splitRecipients("a@example.com; b@example.com"))
    }

    @Test
    fun `builds UTF-8 headers and body without injection`() {
        val message = SmtpMessage.build(
            from = "from@example.com",
            recipients = listOf("to@example.com"),
            subject = "验证码\r\nBcc: evil@example.com",
            body = "验证码 123456",
            now = 1_700_000_000_000L,
        )
        assertTrue(message.contains("Subject: =?UTF-8?B?"))
        assertFalse(message.contains("\r\nBcc:"))
        assertTrue(message.contains("From: from@example.com"))
        assertTrue(message.contains("To: to@example.com"))
        assertTrue(message.contains("Content-Transfer-Encoding: base64"))
    }

    @Test
    fun `dot stuffs lines beginning with a period`() {
        assertEquals("\r\n..hello\r\n", SmtpMessage.dotStuff("\r\n.hello\r\n"))
    }
}
