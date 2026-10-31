package com.dodocomm.app.core.crash

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLoggerTest {
    @Test
    fun `text report contains version and stack`() {
        val text = CrashLogger.toText(
            listOf(
                CrashEntry(
                    time = 1_700_000_000_000L,
                    versionName = "1.10.0",
                    thread = "main",
                    stackTrace = "java.lang.RuntimeException: boom\n\tat com.dodocomm.app.Test.run(Test.kt:1)",
                ),
            ),
        )
        assertTrue(text.contains("v1.10.0"))
        assertTrue(text.contains("main"))
        assertTrue(text.contains("RuntimeException: boom"))
    }
}
