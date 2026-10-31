package com.dodocomm.app.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dodocomm.app.R
import com.dodocomm.app.ui.MainActivity

/** Ongoing notification for background APK downloads. */
object UpdateDownloadNotifier {
    const val NOTIFICATION_ID = 10_003
    const val CHANNEL_ID = "update_download"

    fun showProgress(context: Context, versionName: String, percent: Int) {
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dodoco)
            .setContentTitle("正在下载 DodoComm v$versionName")
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun showReady(context: Context, versionName: String, apkPath: String) {
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val install = installIntent(context, apkPath)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dodoco)
            .setContentTitle("DodoComm v$versionName 已下载")
            .setContentText("点按安装更新")
            .setContentIntent(install)
            .addAction(R.drawable.ic_stat_dodoco, "安装", install)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            10_010,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun installIntent(context: Context, apkPath: String): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
            action = UpdateInstallReceiver.ACTION_INSTALL
            putExtra(UpdateInstallReceiver.EXTRA_APK_PATH, apkPath)
        }
        return PendingIntent.getBroadcast(
            context,
            10_011,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "后台下载应用更新时显示进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
