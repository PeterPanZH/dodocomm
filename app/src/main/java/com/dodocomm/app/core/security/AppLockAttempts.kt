package com.dodocomm.app.core.security

import android.content.Context

/**
 * Process-independent PIN failure throttle. The in-memory UI counter alone can
 * be reset by killing the activity, so the lockout deadline is persisted here.
 */
object AppLockAttempts {
    private const val PREFS = "dodocomm_lockout"
    private const val KEY_FAILURES = "failures"
    private const val KEY_UNTIL = "locked_until"
    private const val MAX_FAILURES = 5
    private const val LOCKOUT_MS = 30_000L

    fun lockedUntil(context: Context, now: Long = System.currentTimeMillis()): Long {
        val until = prefs(context).getLong(KEY_UNTIL, 0L)
        return if (until > now) until else 0L
    }

    /** @return the new lockout deadline, or 0 when the user may retry immediately. */
    fun recordFailure(context: Context, now: Long = System.currentTimeMillis()): Long {
        val prefs = prefs(context)
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        if (failures < MAX_FAILURES) {
            prefs.edit().putInt(KEY_FAILURES, failures).apply()
            return 0L
        }
        val until = now + LOCKOUT_MS
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_UNTIL, until).apply()
        return until
    }

    fun recordSuccess(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
