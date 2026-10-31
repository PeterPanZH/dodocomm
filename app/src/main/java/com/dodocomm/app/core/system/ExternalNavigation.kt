package com.dodocomm.app.core.system

import android.os.SystemClock

/**
 * Marks the next app leave as an external navigation (permission dialog, SAF
 * picker, system settings, installer) instead of Home/Recents. When "hide from
 * recents" is enabled, MainActivity must not finish its task for these leaves,
 * otherwise the system dialog loses its host and the app appears to crash.
 */
object ExternalNavigation {
    private var suppressUntil = 0L

    @Synchronized
    fun suppressNextLeave(durationMs: Long = 8_000L) {
        suppressUntil = SystemClock.elapsedRealtime() + durationMs
    }

    @Synchronized
    fun consumeSuppression(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now > suppressUntil) {
            suppressUntil = 0L
            return false
        }
        suppressUntil = 0L
        return true
    }
}
