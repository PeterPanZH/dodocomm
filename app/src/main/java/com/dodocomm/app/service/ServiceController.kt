package com.dodocomm.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dodocomm.app.AppContainer

/**
 * Small façade that the UI, receivers and workers use to control the
 * [ForwardService] without touching Intents themselves.
 *
 * Every start attempt is wrapped in try/catch: on Android 12+ a background
 * start can legitimately be rejected (`ForegroundServiceStartNotAllowedException`)
 * and that must never crash the caller (e.g. a boot receiver).
 */
class ServiceController(
    private val context: Context,
    private val container: AppContainer,
) {

    fun isServiceRunning(): Boolean = ForwardService.isRunning

    /** Requests a foreground-service start. @return true when the start was accepted. */
    fun start(): Boolean {
        val intent = Intent(context, ForwardService::class.java).setAction(ForwardService.ACTION_START)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // Includes ForegroundServiceStartNotAllowedException / IllegalStateException.
            Log.w(TAG, "启动前台服务被拒绝: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * Starts the service only when forwarding is enabled, the user kept the
     * foreground service on and Bark is configured. Used by boot/maintenance.
     */
    fun startIfEnabled(): Boolean {
        val settings = container.settings.current()
        if (!settings.forwardingEnabled) return false
        if (!settings.foregroundServiceEnabled) return false
        if (!settings.hasAnyConfiguredChannel) return false
        return start()
    }

    /** Stops the service (and its notification) without changing settings. */
    fun stop() {
        try {
            context.stopService(Intent(context, ForwardService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "停止前台服务失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Persists the master switch and applies its side effects: start/stop the
     * foreground service and schedule/cancel background work.
     *
     * Enabling only starts the service once Bark is fully configured
     * ([startIfEnabled]); the UI calls [startIfEnabled] again right after the
     * user saves the server URL / Device Key, so an unconfigured app never shows
     * a "常驻转发中" foreground-service notification.
     *
     * Disabling also cancels the retry queue: a paused forwarder must not keep
     * pushing old messages in the background.
     */
    fun setForwardingEnabled(enabled: Boolean) {
        container.settings.update { it.copy(forwardingEnabled = enabled, pauseUntil = 0L) }
        if (!enabled) container.workScheduler.cancelPauseResume()
        if (enabled) {
            startIfEnabled()
            container.workScheduler.ensureMaintenanceScheduled()
        } else {
            container.workScheduler.cancelMaintenance()
            container.workScheduler.cancelRetry()
            container.workScheduler.cancelServiceRevive()
            stop()
        }
    }

    /** Pauses forwarding for [minutes] without stopping the foreground service. */
    fun pauseFor(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        container.settings.update { it.copy(pauseUntil = until) }
        container.workScheduler.schedulePauseResume(until)
        refreshNotification()
        com.dodocomm.app.widget.WidgetUpdater.updateAll(context)
        Log.i(TAG, "临时暂停转发 $minutes 分钟")
    }

    /** Clears a temporary pause early. */
    fun resumeNow() {
        container.settings.update { it.copy(pauseUntil = 0L) }
        container.workScheduler.cancelPauseResume()
        refreshNotification()
        com.dodocomm.app.widget.WidgetUpdater.updateAll(context)
        Log.i(TAG, "手动恢复转发")
    }

    /** Rebuilds the ongoing notification only when the service is running. */
    fun refreshNotification() {
        NotificationHelper.updateOngoing(context, container)
    }

    private companion object {
        const val TAG = "SMS2BARK"
    }
}
