package com.dodocomm.app.core.update

import android.content.Context

/**
 * Remembers when the last *automatic* update check happened and applies an
 * exponential backoff after failures.
 *
 * Intervals:
 *  - success / healthy: once every 24 h
 *  - failure: 1 h, 2 h, 4 h, ... capped at 24 h
 *
 * Manual checks ignore this schedule; only automatic checks are throttled.
 */
class UpdatePrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastCheckedAt(): Long = prefs.getLong(KEY_LAST_CHECKED_AT, 0L)

    fun failureCount(): Int = prefs.getInt(KEY_FAILURES, 0)

    fun nextAllowedAt(): Long = prefs.getLong(KEY_NEXT_ALLOWED_AT, 0L)

    fun autoCheckDue(now: Long = System.currentTimeMillis()): Boolean = now >= nextAllowedAt()

    fun recordSuccess(now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_LAST_CHECKED_AT, now)
            .putInt(KEY_FAILURES, 0)
            .putLong(KEY_NEXT_ALLOWED_AT, now + CHECK_INTERVAL_MS)
            .apply()
    }

    fun recordFailure(now: Long = System.currentTimeMillis()) {
        val failures = failureCount() + 1
        prefs.edit()
            .putLong(KEY_LAST_CHECKED_AT, now)
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_NEXT_ALLOWED_AT, now + backoffDelay(failures))
            .apply()
    }

    companion object {
        const val CHECK_INTERVAL_MS: Long = 24L * 60 * 60 * 1000
        const val FIRST_RETRY_MS: Long = 60L * 60 * 1000
        const val MAX_BACKOFF_MS: Long = 24L * 60 * 60 * 1000

        private const val PREFS_NAME = "dodocomm_update"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_FAILURES = "failure_count"
        private const val KEY_NEXT_ALLOWED_AT = "next_allowed_at"

        /** Backoff after [failures] consecutive failures (1-based). */
        fun backoffDelay(failures: Int): Long {
            if (failures <= 0) return 0L
            val exponent = (failures - 1).coerceAtMost(20)
            val delay = FIRST_RETRY_MS shl exponent
            return delay.coerceAtMost(MAX_BACKOFF_MS)
        }
    }
}
