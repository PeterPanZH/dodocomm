package com.dodocomm.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Sanity tests for the SMS de-duplication key. */
class HashingTest {

    @Test
    fun `sha256 matches the standard test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256("abc"),
        )
    }

    @Test
    fun `dedupe key is stable and sensitive to every component`() {
        val first = Hashing.sha256("10086|1700000000000|你好")
        val second = Hashing.sha256("10086|1700000000000|你好")
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertNotEquals(first, Hashing.sha256("10086|1700000000001|你好"))
        assertNotEquals(first, Hashing.sha256("10087|1700000000000|你好"))
        assertNotEquals(first, Hashing.sha256("10086|1700000000000|您好"))
    }
}
