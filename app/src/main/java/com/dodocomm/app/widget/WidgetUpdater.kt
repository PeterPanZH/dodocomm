package com.dodocomm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dodocomm.app.R
import com.dodocomm.app.appContainer
import com.dodocomm.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds and refreshes the 2x2 home-screen widget. */
object WidgetUpdater {
    const val ACTION_TOGGLE_PAUSE = "com.dodocomm.app.action.WIDGET_TOGGLE_PAUSE"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, DodoWidgetProvider::class.java))
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val settings = context.appContainer.settings.current()
        val paused = settings.isPaused()
        val stats = context.appContainer.history.stats.value

        val statusText = when {
            !settings.forwardingEnabled -> context.getString(R.string.widget_status_off)
            paused -> context.getString(R.string.widget_status_paused)
            else -> context.getString(R.string.widget_status_on)
        }
        val toggleText = when {
            !settings.forwardingEnabled -> context.getString(R.string.widget_action_open)
            paused -> context.getString(R.string.widget_action_resume)
            else -> context.getString(R.string.widget_action_pause)
        }
        val statusDetail = if (paused) {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            context.getString(R.string.widget_status_paused_until, formatter.format(Date(settings.pauseUntil)))
        } else {
            statusText
        }
        val views = RemoteViews(context.packageName, R.layout.widget_dodocomm).apply {
            setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            setTextViewText(R.id.widget_status, statusDetail)
            setTextViewText(R.id.widget_count, stats.sent.toString())
            setTextViewText(R.id.widget_toggle, toggleText)
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
            setOnClickPendingIntent(R.id.widget_open, openApp(context))
            setOnClickPendingIntent(
                R.id.widget_toggle,
                if (settings.forwardingEnabled) togglePausePendingIntent(context) else openApp(context),
            )
        }
        manager.updateAppWidget(widgetId, views)
    }

    fun togglePause(context: Context) {
        val settings = context.appContainer.settings.current()
        val controller = context.appContainer.serviceController
        when {
            !settings.forwardingEnabled -> Unit
            settings.isPaused() -> controller.resumeNow()
            else -> controller.pauseFor(30)
        }
        updateAll(context)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun togglePausePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_TOGGLE_PAUSE)
        return PendingIntent.getBroadcast(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
