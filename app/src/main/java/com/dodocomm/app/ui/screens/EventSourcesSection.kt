@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dodocomm.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.system.ExternalNavigation
import com.dodocomm.app.events.DodoNotificationListener
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.FilterListEditor
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.Text
import com.dodocomm.app.ui.components.TokenChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun openAppDetails(context: android.content.Context) {
    ExternalNavigation.suppressNextLeave()
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"),
            ),
        )
    }
}

@Composable
fun EventSourcesSection(
    viewModel: AppViewModel,
    onOpenSmsTemplate: () -> Unit,
    onOpenSmsRules: () -> Unit,
    onOpenRouting: (com.dodocomm.app.core.model.ForwardSource) -> Unit,
    onOpenCallRules: () -> Unit,
    onOpenAppRules: () -> Unit,
    onOpenTemplate: (com.dodocomm.app.core.model.ForwardSource) -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            SectionHeader("短信")
            SectionCard {
                SettingSwitchRow(
                    title = "转发短信",
                    checked = settings.smsForwardEnabled,
                    onCheckedChange = viewModel::setSmsForwardEnabled,
                    subtitle = "收到短信后按规则和模板推送",
                    icon = Icons.Rounded.Sms,
                )
                RowDivider()
                SettingActionRow(
                    title = "短信模板",
                    subtitle = "标题、正文与设备名称",
                    value = "编辑",
                    icon = Icons.Rounded.Edit,
                    onClick = onOpenSmsTemplate,
                )
                RowDivider()
                SettingActionRow(
                    title = "短信转发规则",
                    subtitle = "发送者黑白名单与关键词过滤",
                    value = if (settings.hasFilters) "已启用" else "未设置",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = onOpenSmsRules,
                )
                RowDivider()
                SettingActionRow(
                    title = "短信路由规则",
                    subtitle = "按发送者、内容或时间段选择通道",
                    value = "${settings.routingRules.count { it.source == com.dodocomm.app.core.model.ForwardSource.SMS }} 条",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = { onOpenRouting(com.dodocomm.app.core.model.ForwardSource.SMS) },
                )
            }
        }

        Column {
            SectionHeader("电话")
            SectionCard {
                SettingSwitchRow(
                    title = "转发来电",
                    checked = settings.phoneForwardEnabled,
                    onCheckedChange = viewModel::setPhoneForwardEnabled,
                    subtitle = "来电和未接来电会推送到当前默认推送通道",
                    icon = Icons.Rounded.Phone,
                )
                RowDivider()
                SettingActionRow(
                    title = "电话模板",
                    subtitle = "来电与未接来电的标题和正文",
                    value = "编辑",
                    icon = Icons.Rounded.Edit,
                    onClick = { onOpenTemplate(com.dodocomm.app.core.model.ForwardSource.CALL) },
                )
                RowDivider()
                SettingActionRow(
                    title = "电话路由规则",
                    subtitle = "按号码、来电类型或时间段选择通道",
                    value = "${settings.routingRules.count { it.source == com.dodocomm.app.core.model.ForwardSource.CALL }} 条",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = { onOpenRouting(com.dodocomm.app.core.model.ForwardSource.CALL) },
                )
                RowDivider()
                SettingActionRow(
                    title = "电话转发规则",
                    subtitle = "号码白名单与黑名单",
                    value = "${settings.callWhitelist.size + settings.callBlacklist.size} 条",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = onOpenCallRules,
                )
                if (settings.phoneForwardEnabled) {
                    RowDivider()
                    SettingSwitchRow(
                        title = "来电提醒",
                        checked = settings.phoneForwardIncoming,
                        onCheckedChange = viewModel::setPhoneForwardIncoming,
                        subtitle = "电话响铃时立即推送",
                    )
                    RowDivider()
                    SettingSwitchRow(
                        title = "未接来电提醒",
                        checked = settings.phoneForwardMissed,
                        onCheckedChange = viewModel::setPhoneForwardMissed,
                        subtitle = "未接听时补发一条提醒",
                    )
                    RowDivider()
                    SettingActionRow(
                        title = "测试电话事件",
                        subtitle = "发送一条模拟来电到当前推送通道",
                        value = "测试",
                        icon = Icons.Rounded.Phone,
                        onClick = viewModel::testPhoneEvent,
                    )
                }
            }
        }

        Column {
            SectionHeader("应用通知")
            SectionCard {
                SettingSwitchRow(
                    title = "转发应用通知",
                    checked = settings.notificationForwardEnabled,
                    onCheckedChange = viewModel::setNotificationForwardEnabled,
                    subtitle = "通过通知使用权捕获其他应用的通知",
                    icon = Icons.Rounded.NotificationsActive,
                )
                RowDivider()
                SettingActionRow(
                    title = "应用通知模板",
                    subtitle = "通知标题和正文的展示方式",
                    value = "编辑",
                    icon = Icons.Rounded.Edit,
                    onClick = { onOpenTemplate(com.dodocomm.app.core.model.ForwardSource.APP_NOTIFICATION) },
                )
                RowDivider()
                SettingActionRow(
                    title = "应用通知路由规则",
                    subtitle = "按应用、通知内容或时间段选择通道",
                    value = "${settings.routingRules.count { it.source == com.dodocomm.app.core.model.ForwardSource.APP_NOTIFICATION }} 条",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = { onOpenRouting(com.dodocomm.app.core.model.ForwardSource.APP_NOTIFICATION) },
                )
                RowDivider()
                SettingActionRow(
                    title = "应用转发规则",
                    subtitle = "应用白名单与黑名单",
                    value = "${settings.notificationSelectedPackages.size + settings.notificationBlacklistPackages.size} 个",
                    icon = Icons.Rounded.Badge,
                    onClick = onOpenAppRules,
                )
                if (settings.notificationForwardEnabled) {
                    RowDivider()
                    SettingSwitchRow(
                        title = "忽略常驻通知",
                        checked = settings.notificationIgnoreOngoing,
                        onCheckedChange = viewModel::setNotificationIgnoreOngoing,
                        subtitle = "忽略前台服务、下载进度等持续更新的通知",
                    )
                    RowDivider()
                    SettingActionRow(
                        title = "测试应用通知",
                        subtitle = "发送一条模拟应用通知到当前推送通道",
                        value = "测试",
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = viewModel::testNotificationEvent,
                    )
                }
            }
        }
    }
}
