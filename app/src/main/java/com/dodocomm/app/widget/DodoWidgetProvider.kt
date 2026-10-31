package com.dodocomm.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.dodocomm.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Common update logic for all DodoComm widget sizes. */
open class BaseDodoWidgetProvider : AppWidgetProvider() {
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        WidgetUpdater.updateOne(context.applicationContext, appWidgetManager, appWidgetId)
    }
}

/** 2x2 compact card, resizable. */
class DodoWidgetProvider : BaseDodoWidgetProvider()

/** 4x2 wide card. */
class DodoWideWidgetProvider : BaseDodoWidgetProvider()

/** 4x1 status bar. */
class DodoBarWidgetProvider : BaseDodoWidgetProvider()

/** 2x3 tall card. */
class DodoTallWidgetProvider : BaseDodoWidgetProvider()
