package com.dodocomm.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodocomm.app.Sms2BarkApp

/** Clears a temporary "暂停 30 分钟" and refreshes the ongoing notification. */
class ResumeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as Sms2BarkApp).container
        return try {
            val settings = container.settings.current()
            if (settings.pauseUntil > 0L) {
                container.settings.update { it.copy(pauseUntil = 0L) }
                container.serviceController.refreshNotification()
                Log.i("SMS2BARK", "临时暂停已自动恢复")
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("SMS2BARK", "恢复暂停失败: ${t.javaClass.simpleName}", t)
            Result.success()
        }
    }
}
