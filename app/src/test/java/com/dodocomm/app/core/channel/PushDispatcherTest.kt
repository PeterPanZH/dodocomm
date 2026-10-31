package com.dodocomm.app.core.channel

import com.dodocomm.app.bark.BarkClient
import com.dodocomm.app.core.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class PushDispatcherTest {
    @Test
    fun `dispatch with no active channels fails cleanly`() = runBlocking {
        val result = PushDispatcher(BarkClient()).dispatch(AppSettings(), "title", "body")
        assertFalse(result.success)
    }

    @Test
    fun `dispatch result requires every channel to succeed`() {
        val result = DispatchResult(
            listOf(
                ChannelSendResult("a", "A", ChannelType.BARK, true, "ok"),
                ChannelSendResult("b", "B", ChannelType.NTFY, false, "failed"),
            ),
        )
        assertFalse(result.success)
    }
}
