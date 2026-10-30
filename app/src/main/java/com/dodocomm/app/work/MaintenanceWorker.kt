package com.dodocomm.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodocomm.app.Sms2BarkApp
import com.dodocomm.app.core.db.HistoryDao

/**
 * 15-minute self-healing pass:
 *  1. prune history to [HistoryDao.MAX_HISTORY_ROWS],
 *  2. retry pending/failed pushes (enqueueing a network-constrained RetryWorker
 *     if a retryable row exists but the device is offline),
 *  3. restore the foreground service if it was killed while enabled.
 *
 * Periodic work never returns `retry` — the next interval is the retry.
 */
class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as Sms2BarkApp).container
        return try {
            container.history.prune(HistoryDao.MAX_HISTORY_ROWS)
            if (container.forwardEngine.retryPending()) {
                container.workScheduler.enqueueRetry()
            }
            runCatching { container.serviceController.startIfEnabled() }
                .onFailure { Log.w(TAG, "维护任务恢复前台服务失败: ${it.javaClass.simpleName}") }
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "维护任务失败: ${t.javaClass.simpleName}: ${t.message}")
            // Do not ask WorkManager to retry: the periodic worker runs again in
            // 15 minutes and a persistent error should not create a backoff storm.
            Result.success()
        }
    }

    private companion object {
        const val TAG = "SMS2BARK"
    }
}
