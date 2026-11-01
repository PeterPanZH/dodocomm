package com.dodocomm.app.widget

import android.content.Context
import android.os.Build
import java.util.Locale

/** Launcher families whose host chrome/padding differs from AOSP. */
enum class WidgetHostFamily {
    COLOR_OS,
    HARMONY_OS,
    MAGIC_OS,
    HYPER_OS,
    ORIGIN_OS,
    ONE_UI,
    STOCK,
}

/**
 * Visual/installation profile for a launcher family.
 *
 * `hostCard` means the OEM draws a rounded "card" around standard widgets, so
 * our inner gradient is replaced by a neutral translucent backdrop to avoid a
 * card-in-card look. `outerInsetDp` is the breathing room we add inside that
 * host container.
 */
data class WidgetHostProfile(
    val family: WidgetHostFamily,
    val hostCard: Boolean,
    val outerInsetDp: Int,
    val shortBarThresholdDp: Int,
    val guide: String,
)

object WidgetHost {
    fun profile(context: Context): WidgetHostProfile = profileOf(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        oplusRom = "",
    )

    fun isColorOS(): Boolean = profileOf(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        oplusRom = "",
    ).family == WidgetHostFamily.COLOR_OS

    internal fun familyOf(manufacturer: String, brand: String, oplusRom: String): WidgetHostFamily {
        val value = "$manufacturer $brand".lowercase(Locale.ROOT)
        return when {
            oplusRom.isNotBlank() ||
                value.contains("oppo") ||
                value.contains("oneplus") ||
                value.contains("realme") -> WidgetHostFamily.COLOR_OS
            value.contains("huawei") || value.contains("harmony") -> WidgetHostFamily.HARMONY_OS
            value.contains("honor") -> WidgetHostFamily.MAGIC_OS
            value.contains("xiaomi") ||
                value.contains("redmi") ||
                value.contains("poco") -> WidgetHostFamily.HYPER_OS
            value.contains("vivo") || value.contains("iqoo") -> WidgetHostFamily.ORIGIN_OS
            value.contains("samsung") -> WidgetHostFamily.ONE_UI
            else -> WidgetHostFamily.STOCK
        }
    }

    internal fun profileOf(
        manufacturer: String,
        brand: String,
        oplusRom: String,
    ): WidgetHostProfile = when (familyOf(manufacturer, brand, oplusRom)) {
        WidgetHostFamily.COLOR_OS -> WidgetHostProfile(
            family = WidgetHostFamily.COLOR_OS,
            hostCard = true,
            outerInsetDp = 1,
            shortBarThresholdDp = 90,
            guide = "ColorOS：标准安卓组件在「双指捏合 → 卡片 → 滑到底部 → 插件」中添加；系统「卡片」需通过 OPPO CardWidget 授权后才会出现在卡片中心。",
        )
        WidgetHostFamily.HARMONY_OS -> WidgetHostProfile(
            family = WidgetHostFamily.HARMONY_OS,
            hostCard = true,
            outerInsetDp = 1,
            shortBarThresholdDp = 80,
            guide = "HarmonyOS：长按桌面空白 → 窗口小工具/服务卡片 → 嘟嘟通讯；标准组件在窗口小工具中添加。",
        )
        WidgetHostFamily.MAGIC_OS -> WidgetHostProfile(
            family = WidgetHostFamily.MAGIC_OS,
            hostCard = true,
            outerInsetDp = 1,
            shortBarThresholdDp = 80,
            guide = "MagicOS：长按桌面空白 → 窗口小工具/服务卡片 → 嘟嘟通讯。",
        )
        WidgetHostFamily.HYPER_OS -> WidgetHostProfile(
            family = WidgetHostFamily.HYPER_OS,
            hostCard = true,
            outerInsetDp = 2,
            shortBarThresholdDp = 80,
            guide = "HyperOS/MIUI：长按桌面空白 → 添加小部件 → 嘟嘟通讯。",
        )
        WidgetHostFamily.ORIGIN_OS -> WidgetHostProfile(
            family = WidgetHostFamily.ORIGIN_OS,
            hostCard = true,
            outerInsetDp = 2,
            shortBarThresholdDp = 80,
            guide = "OriginOS：长按桌面空白 → 原子组件/桌面挂件 → 嘟嘟通讯。",
        )
        WidgetHostFamily.ONE_UI -> WidgetHostProfile(
            family = WidgetHostFamily.ONE_UI,
            hostCard = false,
            outerInsetDp = 3,
            shortBarThresholdDp = 70,
            guide = "One UI：长按桌面空白 → 小组件 → 嘟嘟通讯。",
        )
        WidgetHostFamily.STOCK -> WidgetHostProfile(
            family = WidgetHostFamily.STOCK,
            hostCard = false,
            outerInsetDp = 4,
            shortBarThresholdDp = 70,
            guide = "长按桌面空白 → 小组件/Widgets → 嘟嘟通讯。",
        )
    }
}
