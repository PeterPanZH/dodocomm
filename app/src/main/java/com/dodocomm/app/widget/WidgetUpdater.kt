package com.dodocomm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.dodocomm.app.R
import com.dodocomm.app.appContainer
import com.dodocomm.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Builds and refreshes every DodoComm home-screen widget size. */
object WidgetUpdater {
    const val ACTION_TOGGLE_PAUSE = "com.dodocomm.app.action.WIDGET_TOGGLE_PAUSE"

    private val providerClasses = listOf(
        DodoWidgetProvider::class.java,
        DodoWideWidgetProvider::class.java,
        DodoBarWidgetProvider::class.java,
        DodoTallWidgetProvider::class.java,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providerClasses.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            ids.forEach { id -> updateOne(context, manager, id) }
        }
    }

    fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val settings = context.appContainer.settings.current()
        val paused = settings.isPaused()
        val stats = context.appContainer.history.stats.value
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val layout = if (widthDp <= 0 || heightDp <= 0) {
            layoutForProvider(manager, widgetId)
        } else {
            chooseLayout(widthDp, heightDp)
        }

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

        val views = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
            setTextViewText(R.id.widget_status, statusDetail)
            setTextViewText(R.id.widget_count, stats.sent.toString())
            setTextViewText(R.id.widget_toggle, toggleText)
            // Compact cards can be resized very small; hide non-essential
            // text instead of letting OEM launchers clip the button row.
            if (layout == R.layout.widget_dodocomm && (heightDp in 1..114 || widthDp in 1..125)) {
                setViewVisibility(R.id.widget_count_label, android.view.View.GONE)
            }
            if (layout == R.layout.widget_dodocomm_bar) {
                setViewVisibility(R.id.widget_count_label, android.view.View.GONE)
            }
            val outerPadding = outerPaddingDp(context)
            val px = (outerPadding * context.resources.displayMetrics.density).roundToInt()
            setViewPadding(R.id.widget_root, px, px, px, px)
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

    private fun layoutForProvider(manager: AppWidgetManager, widgetId: Int): Int =
        when (manager.getAppWidgetInfo(widgetId)?.provider?.className) {
            DodoWideWidgetProvider::class.java.name -> R.layout.widget_dodocomm_wide
            DodoBarWidgetProvider::class.java.name -> R.layout.widget_dodocomm_bar
            DodoTallWidgetProvider::class.java.name -> R.layout.widget_dodocomm_tall
            else -> R.layout.widget_dodocomm
        }

    private fun chooseLayout(widthDp: Int, heightDp: Int): Int = when {
        heightDp in 1..70 -> R.layout.widget_dodocomm_bar
        heightDp >= 165 -> R.layout.widget_dodocomm_tall
        widthDp >= 230 -> R.layout.widget_dodocomm_wide
        else -> R.layout.widget_dodocomm
    }

    /**
     * OEM launchers add different amounts of host padding/card chrome. Keep our
     * own outer inset small on heavily padded launchers and a touch larger on
     * near-stock hosts so content never touches the rounded host card.
     */
    private fun outerPaddingDp(context: Context): Int {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        return when {
            manufacturer.contains("oppo") ||
                manufacturer.contains("oneplus") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") -> 2
            manufacturer.contains("samsung") -> 3
            else -> 4
        }
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
