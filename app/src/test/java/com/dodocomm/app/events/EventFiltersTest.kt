package com.dodocomm.app.events

import com.dodocomm.app.core.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFiltersTest {
    @Test
    fun `call allowlist only allows matching numbers`() {
        val settings = AppSettings(callWhitelist = listOf("+8613800138000"))
        assertTrue(EventFilters.callAllowed(settings, "13800138000"))
        assertFalse(EventFilters.callAllowed(settings, "13900139000"))
        assertFalse(EventFilters.callAllowed(settings, null))
    }

    @Test
    fun `call blocklist wins over allowlist`() {
        val settings = AppSettings(
            callWhitelist = listOf("95588"),
            callBlacklist = listOf("95588"),
        )
        assertFalse(EventFilters.callAllowed(settings, "95588"))
    }

    @Test
    fun `empty app allowlist allows all apps`() {
        val settings = AppSettings(notificationSelectedPackages = emptyList())
        assertTrue(EventFilters.appAllowed(settings, "com.example.any"))
    }

    @Test
    fun `app blacklist wins and allowlist applies when set`() {
        val all = AppSettings(notificationBlacklistPackages = listOf("com.example.chat"))
        assertFalse(EventFilters.appAllowed(all, "com.example.chat"))
        assertTrue(EventFilters.appAllowed(all, "com.example.mail"))

        val selected = AppSettings(
            notificationSelectedPackages = listOf("com.example.mail"),
            notificationBlacklistPackages = listOf("com.example.chat"),
        )
        assertTrue(EventFilters.appAllowed(selected, "com.example.mail"))
        assertFalse(EventFilters.appAllowed(selected, "com.example.other"))
        assertFalse(EventFilters.appAllowed(selected, "com.example.chat"))
    }
}
