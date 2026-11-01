package com.dodocomm.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.dodocomm.app.appContainer
import com.dodocomm.app.ui.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Non-exported receiver for widget button taps; reached via an explicit PendingIntent. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetUpdater.ACTION_TOGGLE_PAUSE -> WidgetUpdater.togglePause(context)
            WidgetUpdater.ACTION_TEST_PUSH -> testPush(context)
        }
    }

    private fun testPush(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val settings = appContext.appContainer.settings.current()
            val result = runCatching {
                appContext.appContainer.pushDispatcher.dispatch(
                    settings = settings,
                    title = AppStrings.translate("DodoComm 测试推送"),
                    body = AppStrings.translate("嘟嘟可报到！测试推送成功 ✅"),
                )
            }.getOrNull()
            val message = if (result?.success == true) {
                AppStrings.translate("测试推送已发出")
            } else {
                AppStrings.translate("测试推送失败：") +
                    (result?.message ?: AppStrings.translate("请检查推送通道"))
            }
            WidgetUpdater.updateAll(appContext)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
            pending.finish()
        }
    }
}
