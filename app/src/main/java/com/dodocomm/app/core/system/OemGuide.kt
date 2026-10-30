package com.dodocomm.app.core.system

import android.os.Build

enum class OemFamily { COLOROS, LINEAGEOS, GENERIC }

enum class GuideAction {
    REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    OPEN_AUTO_START_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_DETAILS,
    OPEN_NOTIFICATION_SETTINGS,
    NONE,
}

data class GuideStep(
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val action: GuideAction = GuideAction.NONE,
)

data class OemGuide(
    val family: OemFamily,
    val deviceLabel: String,
    val title: String,
    val subtitle: String,
    val steps: List<GuideStep>,
    val note: String,
)

/**
 * Honest, device-specific background reliability guide. Everything here uses
 * official Android/OEM settings screens and no trickery (no audio playback,
 * no accessibility abuse, no fake notifications).
 */
object OemGuides {
    /** Detects the best guide for the current device. */
    fun detect(): OemGuide = guideFor(detectedFamily(), defaultDeviceLabel())

    /** Brand/ROM family inferred from Build properties. */
    fun detectedFamily(): OemFamily {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val display = (Build.DISPLAY ?: "") + " " + (Build.HOST ?: "")
        return when {
            display.contains("lineage", ignoreCase = true) -> OemFamily.LINEAGEOS
            manufacturer.equals("OnePlus", true) ||
                manufacturer.equals("OPPO", true) ||
                manufacturer.equals("realme", true) -> OemFamily.COLOROS
            else -> OemFamily.GENERIC
        }
    }

    /**
     * Returns the guide for an explicitly chosen family. The UI uses this to let
     * users override a wrong auto-detection (e.g. an unusual ROM or a device
     * that reports a generic manufacturer).
     */
    fun guideFor(family: OemFamily, deviceLabel: String = defaultDeviceLabel()): OemGuide =
        when (family) {
            OemFamily.COLOROS -> colorOsGuide(deviceLabel)
            OemFamily.LINEAGEOS -> lineageGuide(deviceLabel)
            OemFamily.GENERIC -> genericGuide(deviceLabel)
        }

    /** Human-readable device label, e.g. manufacturer + model of the current phone. */
    fun defaultDeviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return buildString {
            append(manufacturer.replaceFirstChar { it.uppercase() })
            if (model.isNotBlank()) append(' ').append(model)
        }.trim()
    }

    private fun colorOsGuide(deviceLabel: String): OemGuide = OemGuide(
        family = OemFamily.COLOROS,
        deviceLabel = deviceLabel.ifBlank { "ColorOS 设备" },
        title = "ColorOS 后台保活向导",
        subtitle = "适用于 ColorOS 等定制系统",
        steps = listOf(
            GuideStep(
                title = "允许自启动",
                description = "设置 → 应用 → 应用自启动，找到「嘟嘟通讯」并允许自启动。这样重启手机后转发服务会自动恢复。",
                actionLabel = "打开自启动设置",
                action = GuideAction.OPEN_AUTO_START_SETTINGS,
            ),
            GuideStep(
                title = "电池不限制",
                description = "设置 → 电池 → 应用耗电管理 →「嘟嘟通讯」，选择「允许完全后台行为」或取消所有限制，避免熄屏后被省电策略冻结。",
                actionLabel = "打开电池设置",
                action = GuideAction.OPEN_BATTERY_SETTINGS,
            ),
            GuideStep(
                title = "允许通知",
                description = "允许通知权限，并保留常驻通知。常驻通知是前台服务的官方形态，也是系统允许长期运行的必要条件。",
                actionLabel = "打开通知设置",
                action = GuideAction.OPEN_NOTIFICATION_SETTINGS,
            ),
            GuideStep(
                title = "锁定最近任务",
                description = "在最近任务界面，从「嘟嘟通讯」卡片顶部下拉锁定（小锁图标），防止一键清理时被结束。",
            ),
            GuideStep(
                title = "关闭省电模式干扰",
                description = "如果开启「省电模式」或「睡眠待机优化」，请把「嘟嘟通讯」加入白名单，或在这些模式下暂时停用它们。",
                actionLabel = "打开电池优化设置",
                action = GuideAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ),
        ),
        note = "ColorOS 对后台管理较严格。完成以上设置后，短信接收（系统广播）仍然是最可靠的通道；前台服务负责在网络恢复后立即补发。嘟嘟可会努力不掉线的！",
    )

    private fun lineageGuide(deviceLabel: String): OemGuide = OemGuide(
        family = OemFamily.LINEAGEOS,
        deviceLabel = deviceLabel.ifBlank { "LineageOS 设备" },
        title = "LineageOS 后台保活向导",
        subtitle = "适用于 LineageOS 等类原生 Android",
        steps = listOf(
            GuideStep(
                title = "电池优化设为不受限制",
                description = "设置 → 应用 → 嘟嘟通讯 → 电池 → 选择「不受限制」。LineageOS 接近原生 Android，一般不需要额外的厂商自启动设置。",
                actionLabel = "申请忽略电池优化",
                action = GuideAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ),
            GuideStep(
                title = "允许通知",
                description = "允许通知权限并保留前台服务通知，确保系统不会因为通知被关闭而限制后台运行。",
                actionLabel = "打开通知设置",
                action = GuideAction.OPEN_NOTIFICATION_SETTINGS,
            ),
            GuideStep(
                title = "检查后台限制",
                description = "设置 → 应用 → 嘟嘟通讯 → 限制后台活动，确认没有开启；同时不要在开发者选项中限制后台进程数量为 0。",
                actionLabel = "打开应用详情",
                action = GuideAction.OPEN_APP_DETAILS,
            ),
            GuideStep(
                title = "保持系统默认的电池策略",
                description = "LineageOS 的 Doze 会在熄屏后延迟普通后台任务，但短信系统广播仍能唤醒应用，前台服务也会维持常驻；无需额外「保活」黑科技。",
            ),
        ),
        note = "LineageOS 没有厂商后台管理器，按 AOSP 标准设置即可。若安装了第三方省电模块/内核调优，请将本应用排除。嘟嘟可会努力不掉线的！",
    )

    private fun genericGuide(deviceLabel: String): OemGuide = OemGuide(
        family = OemFamily.GENERIC,
        deviceLabel = deviceLabel.ifBlank { "Android 设备" },
        title = "后台保活向导",
        subtitle = "通用 Android 设置",
        steps = listOf(
            GuideStep(
                title = "忽略电池优化",
                description = "允许本应用在后台运行，避免系统在熄屏后冻结进程。",
                actionLabel = "申请忽略电池优化",
                action = GuideAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ),
            GuideStep(
                title = "允许通知",
                description = "前台服务需要通知权限；请允许通知并保留常驻通知。",
                actionLabel = "打开通知设置",
                action = GuideAction.OPEN_NOTIFICATION_SETTINGS,
            ),
            GuideStep(
                title = "允许自启动（如有）",
                description = "部分定制系统有「自启动」开关，请允许本应用自启动。",
                actionLabel = "尝试打开自启动设置",
                action = GuideAction.OPEN_AUTO_START_SETTINGS,
            ),
        ),
        note = "不同厂商的后台策略差异较大，以上为通用的正规设置项。嘟嘟可会努力不掉线的！",
    )
}
