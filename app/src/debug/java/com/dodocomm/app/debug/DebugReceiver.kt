package com.dodocomm.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.model.BarkResult
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.core.util.Hashing
import com.dodocomm.app.core.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * DEBUG-ONLY verification harness (never compiled into release builds).
 *
 * Driven from adb to make E2E verification and screenshot capture deterministic:
 *
 *   adb shell am broadcast -a com.dodocomm.app.debug.SET_CONFIG \
 *       --es server http://10.0.2.2:8787 --es key testdevicekey
 *   adb shell am broadcast -a com.dodocomm.app.debug.SEED_DEMO
 *   adb shell am broadcast -a com.dodocomm.app.debug.DUMP_HISTORY
 *   adb shell am broadcast -a com.dodocomm.app.debug.TEST_PUSH
 *   adb shell am broadcast -a com.dodocomm.app.debug.DUMP_STATE
 *   adb shell am broadcast -a com.dodocomm.app.debug.CLEAR_DATA
 *
 * Everything it does goes through the public production contracts
 * (SettingsRepository / HistoryRepository / ForwardEngine / ServiceController),
 * so the harness exercises the real code paths.
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "DEBUG_ACTION=$action")
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SET_CONFIG -> handleSetConfig(appContext, intent)
                    ACTION_TEST_PUSH -> handleTestPush(appContext)
                    ACTION_SEED_DEMO -> handleSeedDemo(appContext)
                    ACTION_DUMP_HISTORY -> handleDumpHistory(appContext)
                    ACTION_DUMP_STATE -> handleDumpState(appContext)
                    ACTION_CLEAR_DATA -> handleClearData(appContext)
                    else -> Log.w(TAG, "DEBUG_UNKNOWN_ACTION=$action")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "DEBUG_ERROR action=$action ${t.javaClass.simpleName}: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private suspend fun handleSetConfig(context: Context, intent: Intent) {
        val container = context.appContainer
        val before = container.settings.current()
        val updated = container.settings.update { current ->
            current.copy(
                serverUrl = intent.getStringExtra(EXTRA_SERVER)?.takeIf { it.isNotBlank() } ?: current.serverUrl,
                deviceKey = intent.getStringExtra(EXTRA_KEY)?.takeIf { it.isNotBlank() } ?: current.deviceKey,
                titleTemplate = intent.getStringExtra(EXTRA_TEMPLATE) ?: current.titleTemplate,
                pushGroup = intent.getStringExtra(EXTRA_GROUP) ?: current.pushGroup,
                forwardingEnabled = intent.booleanExtra(EXTRA_FORWARDING, current.forwardingEnabled),
                foregroundServiceEnabled =
                    intent.booleanExtra(EXTRA_FOREGROUND_SERVICE, current.foregroundServiceEnabled),
                themeMode = ThemeMode.fromName(intent.getStringExtra(EXTRA_THEME) ?: current.themeMode.name),
            )
        }
        Log.i(
            TAG,
            "SET_CONFIG server=${updated.serverUrl} key=${updated.deviceKey} " +
                "forwarding=${updated.forwardingEnabled} fgs=${updated.foregroundServiceEnabled} " +
                "theme=${updated.themeMode} (was forwarding=${before.forwardingEnabled})",
        )
        if (updated.forwardingEnabled && updated.foregroundServiceEnabled) {
            val started = runCatching { container.serviceController.startIfEnabled() }
                .onFailure { Log.e(TAG, "SET_CONFIG service start failed: ${it.message}", it) }
                .getOrDefault(false)
            Log.i(TAG, "SET_CONFIG service_start_requested=$started")
        }
        delay(300)
    }

    private suspend fun handleTestPush(context: Context) {
        val container = context.appContainer
        val settings = container.settings.current()
        Log.i(
            TAG,
            "TEST_PUSH_START endpoint=${settings.pushEndpoint()} configured=${settings.isConfigured}",
        )
        val result = withTimeoutOrNull(45_000L) {
            runCatching { container.forwardEngine.sendTestPush() }
                .getOrElse { t -> BarkResult.failure("异常: ${t.javaClass.simpleName}: ${t.message}") }
        } ?: BarkResult.failure("超时（45s）")
        Log.i(
            TAG,
            "TEST_PUSH_RESULT success=${result.success} code=${result.code} message=${result.message}",
        )
        container.serviceController.refreshNotification()
        container.history.refresh()
        delay(500)
    }

    private suspend fun handleSeedDemo(context: Context) {
        val container = context.appContainer
        container.history.clearAll()
        val now = System.currentTimeMillis()
        val today0 = TimeFormatter.startOfToday(now)
        val elapsedToday = (now - today0).coerceAtLeast(60_000L)
        fun todayAt(fraction: Double): Long = today0 + (elapsedToday * fraction).toLong()
        fun daysAgoAt(days: Long, hour: Int, minute: Int): Long =
            today0 - days * 86_400_000L + hour * 3_600_000L + minute * 60_000L

        val rows = listOf(
            DemoRow(
                sender = "10086",
                body = "【中国移动】您本月账单已出：月租 59.00 元，流量使用 12.4GB/30GB。回复 CXLL 查询套餐余量。",
                receivedAt = todayAt(0.92),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "95588",
                body = "【工商银行】您尾号 8888 的储蓄卡于 10 月 29 日 18:42 在支付宝消费 128.00 元，余额 24,860.12 元。",
                receivedAt = todayAt(0.71),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "106901234567",
                body = "【顺丰速运】您的快件 SF1234567890123 已到菜鸟驿站，取件码 8-2-1234，请于 20:00 前领取。",
                receivedAt = todayAt(0.42),
                simLabel = "SIM 2 · 中国联通",
                subscriptionId = 2,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "张伟",
                body = "晚上一起吃饭吗？老地方见，六点半。",
                receivedAt = todayAt(0.18),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "12306",
                body = "【铁路12306】您购买的 11 月 2 日 G1234 次列车（北京南-上海虹桥）08:00 开，请提前进站候车。",
                receivedAt = daysAgoAt(1, 21, 12),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "10109777",
                body = "【美团】您的外卖订单已于 19:05 送达，请及时取餐并给骑手五星好评～",
                receivedAt = daysAgoAt(1, 15, 47),
                simLabel = "SIM 2 · 中国联通",
                subscriptionId = 2,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "10000",
                body = "【中国电信】您的宽带将于 11 月 1 日到期，回复 Y 可续费，逾期将影响使用。",
                receivedAt = daysAgoAt(1, 8, 30),
                simLabel = null,
                subscriptionId = -1,
                status = ForwardStatus.FAILED,
                attempts = 2,
                error = "连接超时（10s），请检查 Bark 服务器地址",
            ),
            DemoRow(
                sender = "95533",
                body = "【建设银行】您尾号 5521 的信用卡本期账单金额 2,130.50 元，还款日 11 月 8 日。",
                receivedAt = daysAgoAt(2, 19, 5),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.FAILED,
                attempts = 3,
                error = "服务器返回 HTTP 500: upstream error",
            ),
            DemoRow(
                sender = "10086",
                body = "【中国移动】您的验证码为 839201，5 分钟内有效，请勿告知他人。",
                receivedAt = daysAgoAt(2, 11, 26),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "物业服务中心",
                body = "【阳光花园物业】因管道维修，11 月 1 日 08:00-12:00 暂停供水，请提前储水，给您带来不便敬请谅解。",
                receivedAt = daysAgoAt(4, 17, 41),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.SENT,
                attempts = 1,
                response = SUCCESS_RESPONSE,
            ),
            DemoRow(
                sender = "106905555",
                body = "【推广】您关注的商品降价啦，点击链接立减 100 元，退订回 T。",
                receivedAt = daysAgoAt(4, 9, 8),
                simLabel = "SIM 2 · 中国联通",
                subscriptionId = 2,
                status = ForwardStatus.FILTERED,
                attempts = 0,
                error = "命中关键词过滤：退订",
            ),
            DemoRow(
                sender = "106909999",
                body = "【公益短信】节约用水，人人有责。",
                receivedAt = daysAgoAt(9, 14, 33),
                simLabel = "SIM 1 · 中国移动",
                subscriptionId = 1,
                status = ForwardStatus.PENDING,
                attempts = 5,
                error = "网络不可用，已进入重试队列",
            ),
        )

        var inserted = 0
        rows.forEachIndexed { index, row ->
            val title = row.title ?: "来自 ${row.sender}"
            val record = ForwardRecord(
                sender = row.sender,
                body = row.body,
                receivedAt = row.receivedAt,
                createdAt = row.receivedAt + 350L,
                simLabel = row.simLabel,
                subscriptionId = row.subscriptionId,
                status = ForwardStatus.PENDING,
                attempts = 0,
                title = title,
            )
            val dedupeKey = Hashing.sha256("${row.sender}|${row.receivedAt}|${row.body}|demo-$index")
            val id = container.history.insert(record, dedupeKey)
            if (id != -1L) {
                container.history.updateResult(
                    id = id,
                    status = row.status,
                    attempts = row.attempts,
                    error = row.error,
                    response = row.response,
                )
                inserted++
            }
        }
        delay(400)
        Log.i(TAG, "SEED_DEMO inserted=$inserted requested=${rows.size}")
    }

    private suspend fun handleDumpHistory(context: Context) {
        val container = context.appContainer
        container.history.refresh()
        val records = awaitHistory(container)
        Log.i(TAG, "HISTORY_COUNT=${records.size}")
        records.forEach { record ->
            Log.i(TAG, "HISTORY_ROW ${record.toJson()}")
        }
        val stats = container.history.stats.value
        Log.i(
            TAG,
            "HISTORY_STATS sent=${stats.sent} failed=${stats.failed} filtered=${stats.filtered} pending=${stats.pending}",
        )
    }

    private suspend fun handleDumpState(context: Context) {
        val container = context.appContainer
        val settings = container.settings.current()
        container.history.refresh()
        val records = awaitHistory(container)
        Log.i(
            TAG,
            "STATE forwarding=${settings.forwardingEnabled} server=${settings.serverUrl} " +
                "endpoint=${settings.pushEndpoint()} key=${settings.deviceKey} theme=${settings.themeMode} " +
                "serviceRunning=${container.serviceController.isServiceRunning()} history=${records.size} " +
                "lastStatus=${records.firstOrNull()?.status}",
        )
    }

    private suspend fun handleClearData(context: Context) {
        val container = context.appContainer
        container.history.clearAll()
        container.settings.replace(AppSettings())
        delay(300)
        Log.i(TAG, "CLEAR_DATA history=${container.history.records.value.size} settings=defaults")
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun awaitHistory(container: com.dodocomm.app.AppContainer): List<ForwardRecord> {
        var records = container.history.records.value
        var attempt = 0
        while (attempt < 20 && records.isEmpty()) {
            delay(100)
            container.history.refresh()
            records = container.history.records.value
            attempt++
        }
        return records
    }

    private fun ForwardRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sender", sender)
        put("title", title)
        put("status", status.name)
        put("attempts", attempts)
        put("error", error)
        put("receivedAt", receivedAt)
        put("createdAt", createdAt)
        put("simLabel", simLabel)
        put("body", body)
    }

    private data class DemoRow(
        val sender: String,
        val body: String,
        val receivedAt: Long,
        val simLabel: String?,
        val subscriptionId: Int,
        val status: ForwardStatus,
        val attempts: Int,
        val error: String? = null,
        val response: String? = null,
        val title: String? = null,
    )

    companion object {
        const val TAG = "SMS2BARK_DEBUG"

        const val ACTION_SET_CONFIG = "com.dodocomm.app.debug.SET_CONFIG"
        const val ACTION_TEST_PUSH = "com.dodocomm.app.debug.TEST_PUSH"
        const val ACTION_SEED_DEMO = "com.dodocomm.app.debug.SEED_DEMO"
        const val ACTION_DUMP_HISTORY = "com.dodocomm.app.debug.DUMP_HISTORY"
        const val ACTION_DUMP_STATE = "com.dodocomm.app.debug.DUMP_STATE"
        const val ACTION_CLEAR_DATA = "com.dodocomm.app.debug.CLEAR_DATA"

        private const val EXTRA_SERVER = "server"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TEMPLATE = "template"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_THEME = "theme"
        private const val EXTRA_FORWARDING = "forwarding"
        private const val EXTRA_FOREGROUND_SERVICE = "fgservice"

        private const val SUCCESS_RESPONSE = "{\"code\":200,\"message\":\"success\"}"

        private fun Intent.booleanExtra(name: String, default: Boolean): Boolean =
            if (hasExtra(name)) getBooleanExtra(name, default) else default
    }
}
