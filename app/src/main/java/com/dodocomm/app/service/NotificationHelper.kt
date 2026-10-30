package com.dodocomm.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dodocomm.app.AppContainer
import com.dodocomm.app.R
import com.dodocomm.app.core.util.TimeFormatter
import com.dodocomm.app.ui.MainActivity

/**
 * Single place that owns notification channels, the ongoing foreground-service
 * notification and its action buttons.
 *
 * The ongoing notification is a LOW-importance, silent, ongoing notification —
 * the legitimate way to keep a `specialUse` foreground service visible. It is
 * updated only from in-memory StateFlows (no IO) so it is cheap enough to
 * refresh on every forwarded SMS.
 */
object NotificationHelper {

    const val CHANNEL_SERVICE = "sms2bark_service"
    const val CHANNEL_ALERTS = "sms2bark_alerts"

    const val NOTIFICATION_ID_ONGOING = 10_001
    const val NOTIFICATION_ID_BOOT_RECOVERY = 10_002

    private const val TAG = "SMS2BARK"
    private const val REQUEST_CODE_OPEN = 100
    private const val REQUEST_CODE_PAUSE = 101
    private const val REQUEST_CODE_TEST = 102
    private const val REQUEST_CODE_BOOT_RECOVERY = 103

    @Volatile
    private var channelsCreated = false

    /** Idempotent; safe to call from service, workers and receivers. */
    fun createChannels(context: Context) {
        if (channelsCreated) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.notify_channel_service),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notify_channel_service_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notify_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_alerts_desc)
                setShowBadge(true)
            }
            manager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
            channelsCreated = true
        } catch (t: Throwable) {
            Log.w(TAG, "创建通知渠道失败: ${t.javaClass.simpleName}", t)
        }
    }

    /**
     * Builds the ongoing notification from the in-memory settings/history state.
     * [statusText] overrides the default "today + latest SMS" summary (used by
     * the test-push flow).
     */
    fun buildOngoing(
        context: Context,
        container: AppContainer,
        statusText: String? = null,
    ): Notification {
        val settings = container.settings.current()
        val stats = container.history.stats.value
        val last = container.history.lastRecord()
        val text = statusText ?: when {
            !settings.forwardingEnabled -> context.getString(R.string.notify_paused)
            last != null -> context.getString(
                R.string.notify_ongoing_last,
                stats.sent,
                last.sender.ifBlank { context.getString(R.string.notify_unknown_sender) },
                TimeFormatter.relative(last.receivedAt),
            )
            else -> context.getString(R.string.notify_ongoing_idle, stats.sent)
        }
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_dodoco)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .addAction(R.drawable.ic_stat_notify, context.getString(R.string.notify_action_pause), pauseIntent(context))
            .addAction(R.drawable.ic_stat_notify, context.getString(R.string.notify_action_test), testPushIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Refreshes the ongoing notification **only when the service is running**,
     * so the SMS pipeline never posts a stray notification. Never throws: the
     * POST_NOTIFICATIONS permission may be missing/revoked.
     */
    fun updateOngoing(
        context: Context,
        container: AppContainer,
        statusText: String? = null,
    ) {
        if (!ForwardService.isRunning) return
        createChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID_ONGOING, buildOngoing(context, container, statusText))
        } catch (e: SecurityException) {
            Log.w(TAG, "通知权限被撤销，跳过常驻通知更新")
        } catch (t: Throwable) {
            Log.w(TAG, "更新常驻通知失败: ${t.javaClass.simpleName}", t)
        }
    }

    /**
     * Graceful fallback for Android 16 / ColorOS: a boot-time
     * `startForegroundService()` can be rejected when the process was spawned in
     * the background. A user-visible notification is the only reliable nudge;
     * tapping it opens [MainActivity], whose onResume starts the service from a
     * user-visible context (always allowed). Never throws.
     */
    fun showBootRecoveryNotification(context: Context) {
        createChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "通知被关闭，无法显示启动恢复提醒")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_dodoco)
            .setContentTitle(context.getString(R.string.notify_boot_recovery_title))
            .setContentText(context.getString(R.string.notify_boot_recovery_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notify_boot_recovery_text)),
            )
            .setContentIntent(bootRecoveryIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            manager.notify(NOTIFICATION_ID_BOOT_RECOVERY, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "通知权限被撤销，无法显示启动恢复提醒")
        } catch (t: Throwable) {
            Log.w(TAG, "显示启动恢复提醒失败: ${t.javaClass.simpleName}", t)
        }
    }

    fun cancelOngoing(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ONGOING)
        }
    }

    // ------------------------------------------------------------------ intents

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_OPEN,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun bootRecoveryIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_BOOT_RECOVERY,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pauseIntent(context: Context): PendingIntent =
        serviceAction(context, ForwardService.ACTION_STOP, REQUEST_CODE_PAUSE)

    private fun testPushIntent(context: Context): PendingIntent =
        serviceAction(context, ForwardService.ACTION_TEST_PUSH, REQUEST_CODE_TEST)

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, ForwardService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
