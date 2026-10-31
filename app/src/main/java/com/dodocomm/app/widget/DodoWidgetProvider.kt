package com.dodocomm.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.dodocomm.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { appContext.appContainer.history.refreshNow() }
            withContext(Dispatchers.Main) {
                appWidgetIds.forEach { id -> WidgetUpdater.updateOne(appContext, appWidgetManager, id) }
            }
            pending.finish()
        }
    }
}
