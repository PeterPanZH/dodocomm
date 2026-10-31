package com.dodocomm.app.core.diagnostics

import com.dodocomm.app.core.model.TodayStats
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.ui.model.PermissionState

enum class DiagnosticStatus { PASS, WARN, FAIL }

enum class DiagnosticGroup(val label: String) {
    PERMISSIONS("权限"),
    BACKGROUND("后台与电池"),
    NETWORK("网络"),
    CHANNELS("推送通道"),
    DELIVERY("送达与重试"),
}

enum class DiagnosticAction {
    RECEIVE_SMS,
    POST_NOTIFICATIONS,
    READ_PHONE_STATE,
    BATTERY_OPTIMIZATION,
    OPEN_RELIABILITY,
    OPEN_GUIDE,
    OPEN_CHANNELS,
    OPEN_ROUTING,
    OPEN_HISTORY_RETRY,
    OPEN_UPDATE,
}

data class DiagnosticItem(
    val id: String,
    val group: DiagnosticGroup,
    val title: String,
    val detail: String,
    val status: DiagnosticStatus,
    val action: DiagnosticAction? = null,
    val actionLabel: String? = null,
)

data class DiagnosticReport(
    val items: List<DiagnosticItem>,
) {
    val passed: Int get() = items.count { it.status == DiagnosticStatus.PASS }
    val warnings: Int get() = items.count { it.status == DiagnosticStatus.WARN }
    val failed: Int get() = items.count { it.status == DiagnosticStatus.FAIL }
    val total: Int get() = items.size
    val healthy: Boolean get() = failed == 0
    val summary: String
        get() = "$passed 项通过 · $warnings 项提醒 · $failed 项异常"

    fun toShareText(): String = buildString {
        appendLine("DodoComm 运行诊断")
        appendLine(summary)
        DiagnosticGroup.entries.forEach { group ->
            val groupItems = items.filter { it.group == group }
            if (groupItems.isEmpty()) return@forEach
            appendLine()
            appendLine("【${group.label}】")
            groupItems.forEach { item ->
                val mark = when (item.status) {
                    DiagnosticStatus.PASS -> "✓"
                    DiagnosticStatus.WARN -> "!"
                    DiagnosticStatus.FAIL -> "✕"
                }
                appendLine("$mark ${item.title}：${item.detail}")
            }
        }
    }.trim()
}

data class DiagnosticsInput(
    val settings: AppSettings,
    val permissions: PermissionState,
    val stats: TodayStats,
    val serviceRunning: Boolean,
    val networkAvailable: Boolean,
    val updateAvailable: Boolean = false,
)

object DiagnosticsChecker {
    fun check(input: DiagnosticsInput): DiagnosticReport {
        val settings = input.settings
        val permissions = input.permissions
        val items = mutableListOf<DiagnosticItem>()

        items += DiagnosticItem(
            id = "sms_permission",
            group = DiagnosticGroup.PERMISSIONS,
            title = "短信权限",
            detail = if (permissions.receiveSms) "已授权，可以接收新短信" else "未授权，收不到短信，也无法转发",
            status = if (permissions.receiveSms) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            action = if (permissions.receiveSms) null else DiagnosticAction.RECEIVE_SMS,
            actionLabel = "去授权",
        )
        items += DiagnosticItem(
            id = "notification_permission",
            group = DiagnosticGroup.PERMISSIONS,
            title = "通知权限",
            detail = if (permissions.notificationsReady) "已开启，可以看到转发状态" else "未开启，失败和重试提示可能看不到",
            status = if (permissions.notificationsReady) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            action = if (permissions.notificationsReady) null else DiagnosticAction.POST_NOTIFICATIONS,
            actionLabel = "去开启",
        )
        if (!permissions.readPhoneState) {
            items += DiagnosticItem(
                id = "phone_state",
                group = DiagnosticGroup.PERMISSIONS,
                title = "双卡信息（可选）",
                detail = "未授权读取电话状态，SIM 标签可能显示不完整",
                status = DiagnosticStatus.WARN,
                action = DiagnosticAction.READ_PHONE_STATE,
                actionLabel = "去授权",
            )
        }

        items += DiagnosticItem(
            id = "forwarding_enabled",
            group = DiagnosticGroup.BACKGROUND,
            title = "转发开关",
            detail = if (settings.forwardingEnabled) "已在首页打开转发" else "转发已关闭，新短信不会送出",
            status = if (settings.forwardingEnabled) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            action = if (settings.forwardingEnabled) null else DiagnosticAction.OPEN_RELIABILITY,
            actionLabel = "去开启",
        )
        items += DiagnosticItem(
            id = "foreground_service",
            group = DiagnosticGroup.BACKGROUND,
            title = "常驻通知",
            detail = when {
                !settings.foregroundServiceEnabled -> "已关闭，后台收信可能变慢"
                input.serviceRunning -> "常驻服务正在运行"
                else -> "已开启但服务暂未运行，回到首页会自动恢复"
            },
            status = when {
                !settings.foregroundServiceEnabled -> DiagnosticStatus.WARN
                input.serviceRunning -> DiagnosticStatus.PASS
                else -> DiagnosticStatus.WARN
            },
            action = if (settings.foregroundServiceEnabled && input.serviceRunning) null else DiagnosticAction.OPEN_RELIABILITY,
            actionLabel = "去处理",
        )
        items += DiagnosticItem(
            id = "battery_optimization",
            group = DiagnosticGroup.BACKGROUND,
            title = "电池优化",
            detail = if (permissions.batteryOptimizationsIgnored) {
                "已豁免，系统不会限制后台运行"
            } else {
                "未豁免，熄屏后可能被系统限制"
            },
            status = if (permissions.batteryOptimizationsIgnored) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            action = if (permissions.batteryOptimizationsIgnored) null else DiagnosticAction.BATTERY_OPTIMIZATION,
            actionLabel = "去设置",
        )
        items += DiagnosticItem(
            id = "auto_start",
            group = DiagnosticGroup.BACKGROUND,
            title = "开机自动恢复",
            detail = if (settings.autoStartOnBoot) "重启手机后会自动恢复转发" else "重启后需要手动打开 App 才能恢复",
            status = if (settings.autoStartOnBoot) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            action = if (settings.autoStartOnBoot) null else DiagnosticAction.OPEN_RELIABILITY,
            actionLabel = "去开启",
        )

        items += DiagnosticItem(
            id = "network",
            group = DiagnosticGroup.NETWORK,
            title = "网络连接",
            detail = if (input.networkAvailable) "当前网络可用" else "当前没有网络，失败记录会等待重试",
            status = if (input.networkAvailable) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
            action = if (input.networkAvailable) null else DiagnosticAction.OPEN_HISTORY_RETRY,
            actionLabel = "查看重试",
        )

        val activeChannels = settings.activePushChannels()
        items += DiagnosticItem(
            id = "channels_configured",
            group = DiagnosticGroup.CHANNELS,
            title = "推送通道",
            detail = if (activeChannels.isNotEmpty()) {
                "已配置 ${activeChannels.size} 个可用通道：" + activeChannels.joinToString("、") { it.name }
            } else {
                "还没有可用的推送通道，短信无法送出"
            },
            status = if (activeChannels.isNotEmpty()) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            action = if (activeChannels.isNotEmpty()) null else DiagnosticAction.OPEN_CHANNELS,
            actionLabel = "去配置",
        )
        if (settings.routingRules.any { !it.enabled }) {
            items += DiagnosticItem(
                id = "routing_disabled",
                group = DiagnosticGroup.CHANNELS,
                title = "路由规则",
                detail = "有停用的规则，当前不参与匹配",
                status = DiagnosticStatus.WARN,
                action = DiagnosticAction.OPEN_ROUTING,
                actionLabel = "去查看",
            )
        }

        items += DiagnosticItem(
            id = "retry_queue",
            group = DiagnosticGroup.DELIVERY,
            title = "失败与重试",
            detail = when {
                input.stats.pending > 0 -> "${input.stats.pending} 条记录等待重试"
                input.stats.failed > 0 -> "${input.stats.failed} 条失败记录，可打开记录查看原因"
                else -> "没有失败记录"
            },
            status = when {
                input.stats.failed > 0 -> DiagnosticStatus.WARN
                else -> DiagnosticStatus.PASS
            },
            action = if (input.stats.failed > 0) DiagnosticAction.OPEN_HISTORY_RETRY else null,
            actionLabel = "去查看",
        )
        if (input.updateAvailable) {
            items += DiagnosticItem(
                id = "update_available",
                group = DiagnosticGroup.DELIVERY,
                title = "应用更新",
                detail = "发现新版本，更新后体验会更稳定",
                status = DiagnosticStatus.WARN,
                action = DiagnosticAction.OPEN_UPDATE,
                actionLabel = "去更新",
            )
        }
        return DiagnosticReport(items)
    }
}
