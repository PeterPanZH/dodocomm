package com.dodocomm.app.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandTest {

    private val secret = "0123456789abcdef0123456789abcdef"

    @Test
    fun `parses status and pause commands`() {
        val status = RemoteCommandParser.parse("#DODO STATUS 1700000000 abcdef")
        assertEquals(RemoteAction.STATUS, status?.action)
        assertEquals(1700000000L, status?.nonce)

        val pause = RemoteCommandParser.parse("#DODO PAUSE 45 1700000000 abcdef")
        assertEquals(RemoteAction.PAUSE, pause?.action)
        assertEquals(45, pause?.minutes)
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(RemoteCommandParser.parse("hello"))
        assertNull(RemoteCommandParser.parse("#DODO PAUSE nope 1700000000 abc"))
        assertNull(RemoteCommandParser.parse("#DODO STATUS"))
        assertNull(RemoteCommandParser.parse("#OTHER STATUS 1700000000 abc"))
    }

    @Test
    fun `verifies signature and freshness`() {
        val command = RemoteCommand(RemoteAction.RESUME, 0, 1700000000L, "")
        val signature = RemoteCommandParser.sign(secret, command)
        val signed = command.copy(signature = signature)
        assertTrue(RemoteCommandParser.verify(secret, signed))
        assertFalse(RemoteCommandParser.verify(secret, signed.copy(signature = "00")))
        assertTrue(RemoteCommandParser.nonceIsFresh(1700000000L, 1700000000L))
        assertFalse(RemoteCommandParser.nonceIsFresh(1700000000L, 1700000400L))
    }

    @Test
    fun `normalizes phone numbers`() {
        assertEquals("8613800138000", RemoteCommandParser.normalizeNumber("+86 138-0013-8000"))
    }
}
