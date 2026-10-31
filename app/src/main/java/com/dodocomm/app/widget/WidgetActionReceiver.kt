package com.dodocomm.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Non-exported receiver for widget button taps; reached via an explicit PendingIntent. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_TOGGLE_PAUSE) {
            WidgetUpdater.togglePause(context)
        }
    }
}
