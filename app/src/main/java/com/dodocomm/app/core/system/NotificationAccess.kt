package com.dodocomm.app.core.system

import android.content.Context
import android.provider.Settings

/**
 * Single source of truth for notification-listener access.
 *
 * NotificationManagerCompat is the normal probe, but some ColorOS/OnePlus and
 * other OEM builds update the secure setting before their package list catches
 * up. The secure-setting fallback keeps status cards from claiming "not granted"
 * after the user has already enabled access.
 */
object NotificationAccess {
    fun isGranted(context: Context): Boolean {
        val packageName = context.packageName
        val managerEnabled = runCatching {
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(packageName)
        }.getOrDefault(false)
        if (managerEnabled) return true

        val raw = runCatching {
            Settings.Secure.getString(context.contentResolver, SETTING)
        }.getOrNull().orEmpty()
        return listenerSettingContains(raw, packageName)
    }

    internal fun listenerSettingContains(raw: String?, packageName: String): Boolean {
        if (raw.isNullOrBlank() || packageName.isBlank()) return false
        return raw.split(':').any { component ->
            val normalized = component.trim()
            normalized == packageName || normalized.startsWith("$packageName/")
        }
    }

    private const val SETTING = "enabled_notification_listeners"
}
