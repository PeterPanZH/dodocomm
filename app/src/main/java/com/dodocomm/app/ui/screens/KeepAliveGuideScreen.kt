@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dodocomm.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dodocomm.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.system.GuideAction
import com.dodocomm.app.core.system.GuideStep
import com.dodocomm.app.core.system.OemFamily
import com.dodocomm.app.core.system.OemGuide
import com.dodocomm.app.core.system.OemGuides
import com.dodocomm.app.core.system.SystemNavigator
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.IconBubble
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.NoteCard
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.StepNumberBadge
import com.dodocomm.app.ui.model.PermissionState
import com.dodocomm.app.ui.theme.FilteredDark
import com.dodocomm.app.ui.theme.FilteredLight
import com.dodocomm.app.ui.theme.HeroShape
import com.dodocomm.app.ui.theme.LocalBrandGradient
import com.dodocomm.app.ui.theme.LocalIsDarkTheme
import com.dodocomm.app.ui.theme.SentDark
import com.dodocomm.app.ui.theme.SentLight

@Composable
fun KeepAliveGuideScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val detectedFamily = remember { OemGuides.detectedFamily() }
    val detectedDeviceLabel = remember { SystemNavigator.deviceLabel() }
    var manualFamilyName by rememberSaveable { mutableStateOf("") }
    val autoDetected = manualFamilyName.isBlank()
    val selectedFamily = if (autoDetected) {
        detectedFamily
    } else {
        runCatching { OemFamily.valueOf(manualFamilyName) }.getOrDefault(detectedFamily)
    }
    val guide = remember(selectedFamily) { OemGuides.guideFor(selectedFamily) }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSystemState() }

    fun detectedFor(step: GuideStep): Boolean? = when (step.action) {
        GuideAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        GuideAction.OPEN_BATTERY_SETTINGS,
        -> permissions.batteryOptimizationsIgnored

        GuideAction.OPEN_NOTIFICATION_SETTINGS -> permissions.notificationsReady
        else -> null
    }

    fun openAction(action: GuideAction) {
        when (action) {
            GuideAction.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                SystemNavigator.requestIgnoreBatteryOptimizations(context)

            GuideAction.OPEN_BATTERY_SETTINGS -> SystemNavigator.openBatterySettings(context)
            GuideAction.OPEN_AUTO_START_SETTINGS -> SystemNavigator.openAutoStartSettings(context)
            GuideAction.OPEN_APP_DETAILS -> SystemNavigator.openAppDetails(context)
            GuideAction.OPEN_NOTIFICATION_SETTINGS -> SystemNavigator.openNotificationSettings(context)
            GuideAction.NONE -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "保活向导",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "用官方设置让转发更稳定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        ) {
            item(key = "device", contentType = "hero") {
                DeviceCard(
                    guide = guide,
                    detectedDeviceLabel = detectedDeviceLabel,
                    autoDetected = autoDetected,
                )
                Spacer(Modifier.height(14.dp))
            }
            item(key = "family_switcher", contentType = "card") {
                GuideFamilySwitcher(
                    selectedFamily = selectedFamily,
                    autoDetected = autoDetected,
                    detectedFamily = detectedFamily,
                    onSelectAuto = { manualFamilyName = "" },
                    onSelectFamily = { family -> manualFamilyName = family.name },
                )
                Spacer(Modifier.height(20.dp))
            }
            item(key = "status", contentType = "card") {
                PermissionSummaryCard(
                    permissions = permissions,
                    onRequestPhonePermission = {
                        phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                )
                Spacer(Modifier.height(20.dp))
            }
            item(key = "steps_header", contentType = "header") {
                SectionHeader("按顺序设置", subtitle = "完成后系统会尽量保留后台服务")
                Spacer(Modifier.height(10.dp))
            }
            item(key = "steps_card", contentType = "card") {
                SectionCard {
                    guide.steps.forEachIndexed { index, step ->
                        if (index > 0) RowDivider()
                        GuideStepRow(
                            index = index,
                            step = step,
                            detected = detectedFor(step),
                            onAction = { openAction(step.action) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item(key = "actions_header", contentType = "header") {
                SectionHeader("快捷入口")
                Spacer(Modifier.height(10.dp))
            }
            item(key = "actions_card", contentType = "card") {
                GuideShortcutsCard(
                    family = guide.family,
                    context = context,
                )
                Spacer(Modifier.height(20.dp))
            }
            item(key = "note", contentType = "note") {
                NoteCard(
                    title = "为什么这些是正规方式",
                    body = honestNote(guide),
                    icon = Icons.Rounded.Info,
                )
                Spacer(Modifier.height(12.dp))
            }
            item(key = "worker_note", contentType = "note") {
                InfoNote("即使服务被系统清理，每 15 分钟的维护任务也会尝试恢复转发并补发失败短信。")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header pieces
// ---------------------------------------------------------------------------

@Composable
private fun DeviceCard(
    guide: OemGuide,
    detectedDeviceLabel: String,
    autoDetected: Boolean,
) {
    val gradient = LocalBrandGradient.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HeroShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = size.width * 0.5f,
                        center = Offset(size.width * 1.02f, -size.height * 0.1f),
                    )
                }
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = detectedDeviceLabel.ifBlank { guide.deviceLabel },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (autoDetected) "自动识别 · ${guide.subtitle}" else "手动选择 · ${guide.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = familyLabel(guide.family),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

private fun familyLabel(family: OemFamily): String = when (family) {
    OemFamily.COLOROS -> "ColorOS"
    OemFamily.LINEAGEOS -> "LineageOS"
    OemFamily.GENERIC -> "通用 Android"
}

/** Short chip label so all four family options fit on one row. */
private fun familyChipLabel(family: OemFamily): String = when (family) {
    OemFamily.COLOROS -> "ColorOS"
    OemFamily.LINEAGEOS -> "LineageOS"
    OemFamily.GENERIC -> "通用"
}

@Composable
private fun GuideFamilySwitcher(
    selectedFamily: OemFamily,
    autoDetected: Boolean,
    detectedFamily: OemFamily,
    onSelectAuto: () -> Unit,
    onSelectFamily: (OemFamily) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = Icons.Rounded.PhoneAndroid, size = 34, iconSize = 18)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "向导机型",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (autoDetected) {
                        "已自动识别为 ${familyLabel(detectedFamily)}，识别不准可手动切换"
                    } else {
                        "已手动选择 ${familyLabel(selectedFamily)}，可切回自动识别"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GuideFamilyChip(
                text = "自动",
                selected = autoDetected,
                onClick = onSelectAuto,
            )
            OemFamily.entries.forEach { family ->
                GuideFamilyChip(
                    text = familyChipLabel(family),
                    selected = !autoDetected && selectedFamily == family,
                    onClick = { onSelectFamily(family) },
                )
            }
        }
    }
}

@Composable
private fun GuideFamilyChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun PermissionSummaryCard(
    permissions: PermissionState,
    onRequestPhonePermission: () -> Unit,
) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            GuideStatusCell(
                icon = Icons.Rounded.Sms,
                label = "短信权限",
                ready = permissions.receiveSms,
                modifier = Modifier.weight(1f),
            )
            GuideStatusCell(
                icon = Icons.Rounded.NotificationsActive,
                label = "通知",
                ready = permissions.notificationsReady,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            GuideStatusCell(
                icon = Icons.Rounded.PhoneAndroid,
                label = "电话信息",
                ready = permissions.readPhoneState,
                modifier = Modifier.weight(1f),
            )
            GuideStatusCell(
                icon = Icons.Rounded.BatterySaver,
                label = "电池优化",
                ready = permissions.batteryOptimizationsIgnored,
                modifier = Modifier.weight(1f),
            )
        }
        if (!permissions.readPhoneState) {
            RowDivider()
            SettingActionRow(
                title = "授予电话信息权限",
                subtitle = "模板里的 {sim} / {number} 需要它；不授权也能正常转发短信",
                value = "去授权",
                icon = Icons.Rounded.PhoneAndroid,
                onClick = onRequestPhonePermission,
            )
        }
    }
}

@Composable
private fun GuideStatusCell(
    icon: ImageVector,
    label: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val tonal = when {
        ready -> if (dark) SentDark else SentLight
        else -> if (dark) FilteredDark else FilteredLight
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(
            icon = icon,
            size = 34,
            iconSize = 18,
            containerColor = tonal.container,
            contentColor = tonal.content,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (ready) "已就绪" else "待设置",
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) tonal.content else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Steps
// ---------------------------------------------------------------------------

@Composable
private fun GuideStepRow(
    index: Int,
    step: GuideStep,
    detected: Boolean?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepNumberBadge(index = index)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (detected != null) {
                    DetectedPill(done = detected)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (step.actionLabel != null && step.action != GuideAction.NONE) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = step.actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedPill(done: Boolean) {
    val dark = LocalIsDarkTheme.current
    val tonal = when {
        done -> if (dark) SentDark else SentLight
        else -> if (dark) FilteredDark else FilteredLight
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tonal.container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (done) "已完成" else "待设置",
            style = MaterialTheme.typography.labelSmall,
            color = tonal.content,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// Shortcuts + honest note
// ---------------------------------------------------------------------------

@Composable
private fun GuideShortcutsCard(family: OemFamily, context: Context) {
    SectionCard {
        SettingActionRow(
            title = "打开电池优化设置",
            subtitle = "系统设置 → 电池 → 不受限制",
            icon = Icons.Rounded.BatterySaver,
            onClick = { SystemNavigator.openBatteryOptimizationSettings(context) },
        )
        if (family != OemFamily.LINEAGEOS) {
            RowDivider()
            SettingActionRow(
                title = "打开自启动设置",
                subtitle = "允许应用在重启后自动运行",
                icon = Icons.Rounded.Autorenew,
                onClick = { SystemNavigator.openAutoStartSettings(context) },
            )
        }
        RowDivider()
        SettingActionRow(
            title = "打开通知设置",
            subtitle = "允许通知，保留常驻通知",
            icon = Icons.Rounded.NotificationsActive,
            onClick = { SystemNavigator.openNotificationSettings(context) },
        )
        RowDivider()
        SettingActionRow(
            title = "打开应用详情",
            subtitle = "查看权限与后台限制",
            icon = Icons.Rounded.Info,
            onClick = { SystemNavigator.openAppDetails(context) },
        )
    }
}

private fun honestNote(guide: OemGuide): String = buildString {
    append(guide.note)
    append("\n\n")
    append(
        "为什么这些是正规方式：短信接收使用系统 SMS_RECEIVED 广播，进程被清理也能收到；" +
            "常驻通知是前台服务的官方形态；电池优化豁免与自启动都是厂商公开的系统设置。",
    )
    append("\n\n")
    append(
        "系统的限制：深度休眠或省电模式仍可能延迟后台任务，但短信到达时广播会唤醒应用，" +
            "网络恢复后失败短信会自动补发。本应用不使用后台播放静音音频、无障碍服务或 1px 页面等非常规手段。",
    )
}
