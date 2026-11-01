package com.dodocomm.app.events

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.system.NotificationAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Optional app-notification source. The user must grant notification access in
 * system settings; without it the service is never bound and nothing changes.
 */
class DodoNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cap concurrent notification forwarding so a burst cannot flood channels. */
    private val forwardingSlots = Semaphore(2)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val settings = applicationContext.appContainer.settings.current()
        if (!settings.notificationForwardEnabled) return
        if (!EventFilters.appAllowed(settings, packageName)) return

        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (settings.notificationIgnoreOngoing &&
            (flags and Notification.FLAG_ONGOING_EVENT != 0 || flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        ) return
        // When call forwarding is enabled, avoid double-forwarding the dialer's
        // own incoming-call notification.
        if (settings.phoneForwardEnabled && notification.category == Notification.CATEGORY_CALL) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

        scope.launch {
            forwardingSlots.withPermit {
            runCatching {
                applicationContext.appContainer.eventForwarder.forwardAppNotification(
                    appName = appName,
                    packageName = packageName,
                    notificationTitle = title,
                    notificationText = text,
                    notificationKey = sbn.key,
                    timestamp = sbn.postTime,
                )
            }.onFailure { Log.w(TAG, "通知转发失败: ${it.javaClass.simpleName}") }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "SMS2BARK"

        fun isAccessGranted(context: Context): Boolean = NotificationAccess.isGranted(context)
    }
}
