package com.dodocomm.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns every WorkManager job. Two jobs exist:
 *
 *  - `sms2bark_maintenance` — periodic (15 min) safety net: prune the history
 *    table, retry pending pushes and restore the foreground service if it was
 *    killed. `KEEP` so repeated calls never reschedule the running job.
 *  - `sms2bark_retry` — one-shot, network-constrained push retry with
 *    exponential backoff, enqueued after a failed immediate send.
 */
class WorkScheduler(context: Context) {

    private val appContext: Context = context.applicationContext

    fun ensureMaintenanceScheduled() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(
            MAINTENANCE_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                WORK_MAINTENANCE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancelMaintenance() {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_MAINTENANCE)
        }
    }

    fun enqueueRetry(backoffSeconds: Long = RETRY_BACKOFF_SECONDS, onlyWifi: Boolean = false) {
        val networkType = if (onlyWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<RetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                backoffSeconds.coerceAtLeast(10L),
                TimeUnit.SECONDS,
            )
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_RETRY,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    /** Schedules automatic clearing of a temporary pause at [untilEpochMillis]. */
    fun schedulePauseResume(untilEpochMillis: Long) {
        val delay = (untilEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ResumeWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_PAUSE_RESUME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    fun cancelPauseResume() {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_PAUSE_RESUME)
        }
    }

    fun cancelRetry() {
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_RETRY)
        }
    }

    fun cancelAll() {
        cancelMaintenance()
        cancelRetry()
        cancelPauseResume()
    }

    companion object {
        const val WORK_MAINTENANCE = "sms2bark_maintenance"
        const val WORK_RETRY = "sms2bark_retry"
        const val WORK_PAUSE_RESUME = "sms2bark_pause_resume"

        /** WorkManager's minimum periodic interval. */
        const val MAINTENANCE_INTERVAL_MINUTES = 15L

        /** WorkManager's minimum one-shot backoff is 10s; use a bit more. */
        const val RETRY_BACKOFF_SECONDS = 15L
    }
}
