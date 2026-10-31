package com.dodocomm.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.dodocomm.app.R
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.model.BarkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Persistent `specialUse` foreground service (declared in the manifest with a
 * subtype property). This is the only keep-alive mechanism on top of the SMS
 * broadcast itself plus WorkManager — no audio playback, no accessibility, no
 * 1px activity tricks.
 *
 * The notification is silent/LOW importance and exposes "暂停" + "测试" actions.
 * `START_STICKY` lets the system recreate the service after it was killed;
 * `BootReceiver` additionally restores it after a reboot.
 */
class ForwardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var userStopped = false
    private var foregroundFailed = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY restart (null intent): only revive the service when the
        // user still wants forwarding. Avoid resurrecting a paused service.
        if (intent == null) {
            val settings = applicationContext.appContainer.settings.current()
            if (!settings.forwardingEnabled || !settings.foregroundServiceEnabled) {
                Log.i(TAG, "服务被系统重启但转发已关闭，停止")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Always go foreground first: the system requires it within a few
        // seconds when the service was started with startForegroundService().
        if (!ensureForeground()) return START_NOT_STICKY

        when (intent?.action) {
            ACTION_STOP -> {
                handleStop()
                return START_NOT_STICKY
            }

            ACTION_TEST_PUSH -> handleTestPush()

            ACTION_PAUSE_30 -> {
                applicationContext.appContainer.serviceController.pauseFor(30)
            }

            ACTION_RESUME -> {
                applicationContext.appContainer.serviceController.resumeNow()
            }

            ACTION_REFRESH, ACTION_START, null -> Unit

            else -> Log.w(TAG, "未知 action=${intent.action}")
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the task away does not stop the foreground service, but some
        // OEMs kill it anyway. Schedule a best-effort revive; the periodic
        // MaintenanceWorker remains the final safety net.
        val settings = applicationContext.appContainer.settings.current()
        if (!userStopped && !foregroundFailed &&
            settings.forwardingEnabled && settings.foregroundServiceEnabled
        ) {
            applicationContext.appContainer.workScheduler.enqueueServiceRevive(10)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        if (!userStopped && !foregroundFailed) {
            val settings = applicationContext.appContainer.settings.current()
            if (settings.forwardingEnabled && settings.foregroundServiceEnabled) {
                applicationContext.appContainer.workScheduler.enqueueServiceRevive(10)
            }
        }
        NotificationHelper.cancelOngoing(this)
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ actions

    private fun ensureForeground(statusText: String? = null): Boolean {
        val container = applicationContext.appContainer
        val notification = NotificationHelper.buildOngoing(this, container, statusText)
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_ONGOING,
                notification,
                foregroundServiceType,
            )
            isRunning = true
            true
        } catch (t: Throwable) {
            // e.g. FGS type not permitted on this ROM. Never crash the process;
            // stopping means the SMS broadcast path + WorkManager still work.
            Log.e(TAG, "进入前台服务失败: ${t.javaClass.simpleName}: ${t.message}", t)
            isRunning = false
            foregroundFailed = true
            val settings = applicationContext.appContainer.settings.current()
            if (settings.forwardingEnabled && settings.foregroundServiceEnabled) {
                applicationContext.appContainer.workScheduler.enqueueServiceRevive(30)
                if (NotificationHelper.areNotificationsAllowed(this)) {
                    runCatching { NotificationHelper.showBootRecoveryNotification(this) }
                }
            }
            stopSelf()
            false
        }
    }

    private fun handleStop() {
        Log.i(TAG, "收到暂停指令，停止转发服务")
        userStopped = true
        val container = applicationContext.appContainer
        // Updates settings, cancels periodic/retry work and requests service stop.
        container.serviceController.setForwardingEnabled(false)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        NotificationHelper.cancelOngoing(this)
        isRunning = false
        stopSelf()
    }

    private fun handleTestPush() {
        val container = applicationContext.appContainer
        scope.launch {
            NotificationHelper.updateOngoing(
                this@ForwardService,
                container,
                getString(R.string.notify_test_sending),
            )
            val result: BarkResult = try {
                withTimeout(TEST_PUSH_TIMEOUT_MS) { container.forwardEngine.sendTestPush() }
            } catch (e: TimeoutCancellationException) {
                BarkResult.failure("测试推送超时，请检查 Bark 服务器地址与网络")
            } catch (t: Throwable) {
                BarkResult.failure("测试推送异常：${t.javaClass.simpleName}")
            }
            Log.i(TAG, "测试推送结果 success=${result.success} code=${result.code} message=${result.message}")
            val statusText = if (result.success) {
                getString(R.string.notify_test_success)
            } else {
                getString(R.string.notify_test_failed, result.message)
            }
            NotificationHelper.updateOngoing(this@ForwardService, container, statusText)
        }
    }

    companion object {
        const val ACTION_START = "com.dodocomm.app.action.START"
        const val ACTION_STOP = "com.dodocomm.app.action.STOP"
        const val ACTION_REFRESH = "com.dodocomm.app.action.REFRESH"
        const val ACTION_TEST_PUSH = "com.dodocomm.app.action.TEST_PUSH"
        const val ACTION_PAUSE_30 = "com.dodocomm.app.action.PAUSE_30"
        const val ACTION_RESUME = "com.dodocomm.app.action.RESUME"

        private const val TAG = "SMS2BARK"
        private const val TEST_PUSH_TIMEOUT_MS = 30_000L

        /** Observable service state for [ServiceController]; written on the main thread. */
        @Volatile
        var isRunning: Boolean = false
            internal set
    }
}
