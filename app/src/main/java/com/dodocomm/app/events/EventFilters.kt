package com.dodocomm.app.events

import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.forward.FilterRules

/** Call-number and app-package allow/block lists for non-SMS events. */
object EventFilters {
    fun callAllowed(settings: AppSettings, number: String?): Boolean {
        val target = number?.trim().orEmpty()
        val blacklisted = settings.callBlacklist
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.matches(target) }
        if (blacklisted != null) return false
        val whitelist = settings.callWhitelist.map { it.trim() }.filter { it.isNotEmpty() }
        if (whitelist.isEmpty()) return true
        // A non-empty whitelist means unknown numbers cannot be verified and
        // are therefore blocked.
        if (target.isEmpty()) return false
        return whitelist.any { it.matches(target) }
    }

    fun appAllowed(settings: AppSettings, packageName: String): Boolean {
        if (packageName in settings.notificationBlacklistPackages) return false
        if (!settings.notificationAllApps && packageName !in settings.notificationSelectedPackages) return false
        return true
    }

    private fun String.matches(target: String): Boolean =
        com.dodocomm.app.forward.FilterRules.run { this@matches.matchesSender(target) }
}
