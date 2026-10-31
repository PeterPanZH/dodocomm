package com.dodocomm.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.dodocomm.app.BuildConfig
import com.dodocomm.app.appContainer
import com.dodocomm.app.widget.WidgetUpdater
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Opt-in automation bridge for Tasker / adb / other automation apps.
 *
 * It is disabled by default. When enabled, every command must carry the
 * per-install token shown in Settings → 自动化与组件. Only non-destructive
 * commands are exposed (status, test push, pause, resume, check update).
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action=${intent.action} command=${intent.getStringExtra(EXTRA_COMMAND)}")
        if (intent.action != ACTION_AUTOMATION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val settings = appContext.appContainer.settings.current()
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty().lowercase()
        Log.i(TAG, "automation enabled=${settings.automationEnabled} command=$command")

        if (!settings.automationEnabled || settings.automationToken.isBlank()) {
            finish(pendingResult, false, "自动化接口未开启")
            return
        }
        if (!tokenMatches(token, settings.automationToken)) {
            finish(pendingResult, false, "令牌不正确")
            return
        }

        when (command) {
            "status" -> {
                val stats = appContext.appContainer.history.stats.value
                val extras = Bundle().apply {
                    putBoolean("forwardingEnabled", settings.forwardingEnabled)
                    putBoolean("paused", settings.isPaused())
                    putBoolean("serviceRunning", appContext.appContainer.serviceController.isServiceRunning())
                    putBoolean("automationEnabled", settings.automationEnabled)
                    putInt("activeChannels", settings.activePushChannels().size)
                    putInt("pendingRetries", stats.pending)
                    putInt("sentToday", stats.sent)
                    putString("versionName", BuildConfig.VERSION_NAME)
                    putInt("versionCode", BuildConfig.VERSION_CODE)
                }
                finish(pendingResult, true, "status ok", extras)
            }

            "test_push" -> {
                val channels = settings.activePushChannels()
                if (channels.isEmpty()) {
                    finish(pendingResult, false, "没有可用的推送通道")
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val result = runCatching {
                        appContext.appContainer.pushDispatcher.dispatch(
                            settings = settings,
                            title = "DodoComm 自动化测试推送",
                            body = "嘟嘟可报到！自动化接口测试成功 ✅",
                        )
                    }.getOrNull()
                    finish(
                        pendingResult,
                        result?.success == true,
                        result?.message ?: "测试推送失败",
                    )
                }
            }

            "pause" -> {
                if (!settings.forwardingEnabled) {
                    finish(pendingResult, false, "转发已关闭")
                    return
                }
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 30).coerceIn(1, 1440)
                appContext.appContainer.serviceController.pauseFor(minutes)
                WidgetUpdater.updateAll(appContext)
                finish(pendingResult, true, "已暂停 $minutes 分钟")
            }

            "resume" -> {
                appContext.appContainer.serviceController.resumeNow()
                WidgetUpdater.updateAll(appContext)
                finish(pendingResult, true, "已恢复转发")
            }

            "check_update" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val info = runCatching {
                        appContext.appContainer.updateClient.fetch(
                            BuildConfig.UPDATE_MANIFEST_URL,
                            BuildConfig.VERSION_CODE,
                        )
                    }.getOrNull()
                    val extras = Bundle().apply {
                        putBoolean("updateAvailable", info != null)
                        putString("latestVersion", info?.versionName ?: BuildConfig.VERSION_NAME)
                    }
                    finish(
                        pendingResult,
                        true,
                        if (info != null) "发现新版本 ${info.versionName}" else "已是最新版本",
                        extras,
                    )
                }
            }

            else -> finish(
                pendingResult,
                false,
                "未知命令，可用：status / test_push / pause / resume / check_update",
            )
        }
    }

    private fun finish(
        pendingResult: PendingResult,
        success: Boolean,
        message: String,
        extras: Bundle? = null,
    ) {
        Log.i(TAG, "finish success=$success message=$message")
        runCatching {
            setResultCode(if (success) 1 else 0)
            setResultData(message)
            setResultExtras((extras ?: Bundle()).apply {
                putBoolean("success", success)
                putString("message", message)
            })
        }
        pendingResult.finish()
    }



    private fun tokenMatches(input: String, stored: String): Boolean {
        if (input.isBlank()) return false
        return MessageDigest.isEqual(input.toByteArray(), stored.toByteArray())
    }

    companion object {
        private const val TAG = "DODOCOMM_AUTOMATION"
        const val ACTION_AUTOMATION = "com.dodocomm.app.action.AUTOMATION"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_MINUTES = "minutes"
    }
}
