package com.dodocomm.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodocomm.app.Sms2BarkApp

/**
 * Network-constrained retry of failed/pending pushes.
 *
 * Unique work (`sms2bark_retry`) with exponential backoff; the per-row attempt
 * cap lives in [com.dodocomm.app.forward.ForwardEngine.MAX_ATTEMPTS], so a
 * poisoning row can never retry forever.
 */
class RetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as Sms2BarkApp).container
        return try {
            if (container.forwardEngine.retryPending()) {
                if (runAttemptCount >= MAX_RUN_ATTEMPTS) {
                    Log.w(TAG, "重试次数已达上限，放弃本轮")
                    Result.failure()
                } else {
                    Result.retry()
                }
            } else {
                Result.success()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "重试任务异常: ${t.javaClass.simpleName}: ${t.message}")
            if (runAttemptCount >= MAX_RUN_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private companion object {
        const val TAG = "SMS2BARK"

        /** `Result.retry()` is backed off by WorkManager; cap it defensively. */
        const val MAX_RUN_ATTEMPTS = 8
    }
}
