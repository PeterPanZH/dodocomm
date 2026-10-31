package com.dodocomm.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class DodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> WidgetUpdater.updateOne(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_TOGGLE_PAUSE) {
            WidgetUpdater.togglePause(context)
        }
        super.onReceive(context, intent)
    }
}
