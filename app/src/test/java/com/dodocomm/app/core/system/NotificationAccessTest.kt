package com.dodocomm.app.core.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTest {
    @Test
    fun `secure listener setting accepts component and bare package`() {
        assertTrue(
            NotificationAccess.listenerSettingContains(
                "com.other/.Listener:com.dodocomm.app/com.dodocomm.app.events.DodoNotificationListener",
                "com.dodocomm.app",
            ),
        )
        assertTrue(
            NotificationAccess.listenerSettingContains("com.dodocomm.app", "com.dodocomm.app"),
        )
    }

    @Test
    fun `secure listener setting does not match package prefixes`() {
        assertFalse(
            NotificationAccess.listenerSettingContains(
                "com.dodocomm.app.other/com.dodocomm.app.other.Listener",
                "com.dodocomm.app",
            ),
        )
        assertFalse(NotificationAccess.listenerSettingContains(null, "com.dodocomm.app"))
    }
}
