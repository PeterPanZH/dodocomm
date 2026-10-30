package com.dodocomm.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dodocomm.app.appContainer

/**
 * Restores the forwarding pipeline after a reboot / app update.
 *
 * `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are both FGS-start exemptions on
 * Android 12+, and `specialUse` services are allowed to start from
 * `BOOT_COMPLETED` on Android 15+. Android 16 / some OEMs may still
 * reject the start when the process was spawned in the background; the start is
 * therefore best-effort and, on failure, the user gets a tappable recovery
 * notification instead. The SMS broadcast path keeps working regardless, and
 * the periodic maintenance job retries.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit

            else -> return
        }

        val appContext = context.applicationContext
        try {
            val container = appContext.appContainer
            val settings = container.settings.current()
            val shouldStart = settings.forwardingEnabled && settings.autoStartOnBoot
            val startRequested = if (shouldStart) {
                runCatching { container.serviceController.startIfEnabled() }
                    .onFailure { Log.w(TAG, "boot: FGS start threw ${it.javaClass.simpleName}: ${it.message}") }
                    .getOrDefault(false)
            } else {
                false
            }
            // Always (re)arm the periodic safety net, even when forwarding is
            // currently off: it is the self-healing hook after force-stops.
            container.workScheduler.ensureMaintenanceScheduled()
            if (!startRequested && shouldStart && settings.foregroundServiceEnabled && settings.isConfigured) {
                // Android 16 / OEM blocked the boot-time FGS start. MainActivity
                // onResume will start it from a user-visible context on tap.
                NotificationHelper.showBootRecoveryNotification(appContext)
                Log.w(TAG, "boot: FGS start blocked (Android 16/OEM), recovery notification shown")
            }
            Log.i(
                TAG,
                "boot action=${intent.action} forwarding=${settings.forwardingEnabled} " +
                    "autoStart=${settings.autoStartOnBoot} serviceStartRequested=$startRequested",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "boot 处理失败: ${t.javaClass.simpleName}", t)
        }
    }

    companion object {
        /** Some OEMs (Oppo/OnePlus/Vivo) send this instead of BOOT_COMPLETED. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

        private const val TAG = "SMS2BARK"
    }
}
