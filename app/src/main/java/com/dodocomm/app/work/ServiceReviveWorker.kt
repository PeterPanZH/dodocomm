package com.dodocomm.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodocomm.app.Sms2BarkApp
import com.dodocomm.app.service.NotificationHelper

/**
 * Best-effort restart of the foreground service after boot / task removal / a
 * failed FGS start. On Android 12+ the OS may reject a background FGS start;
 * that is expected and handled by the boot-recovery notification. The SMS
 * broadcast + Periodic MaintenanceWorker remain the fallback either way.
 */
class ServiceReviveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as Sms2BarkApp).container
        val settings = container.settings.current()
        if (!settings.forwardingEnabled ||
            !settings.foregroundServiceEnabled ||
            !settings.hasAnyConfiguredChannel
        ) {
            return Result.success()
        }
        val started = runCatching { container.serviceController.startIfEnabled() }
            .onFailure { Log.w(TAG, "恢复前台服务失败: ${it.javaClass.simpleName}") }
            .getOrDefault(false)
        if (!started) {
            runCatching { NotificationHelper.showBootRecoveryNotification(applicationContext) }
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "SMS2BARK"
    }
}
