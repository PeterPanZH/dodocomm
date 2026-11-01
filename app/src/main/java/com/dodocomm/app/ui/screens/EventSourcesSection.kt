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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val label: String, val packageName: String)

private fun openAppDetails(context: android.content.Context) {
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
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var permissionEpoch by remember { mutableStateOf(0) }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionEpoch++ }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionEpoch++ }
    val hasPhonePermission = remember(permissionEpoch) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val hasCallLogPermission = remember(permissionEpoch) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val notificationAccess = DodoNotificationListener.isAccessGranted(context)
    var appPickerMode by rememberSaveable { mutableStateOf<String?>(null) }
    var templateMode by rememberSaveable { mutableStateOf<String?>(null) }

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
            }
            Spacer(Modifier.height(10.dp))
            InfoNote("短信、电话和应用通知共用推送通道与路由规则，但分别配置内容和模板。")
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
                    onClick = { templateMode = "call" },
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
                        title = "电话权限",
                        subtitle = if (hasPhonePermission) {
                            "已允许检测来电状态"
                        } else {
                            "需要电话权限才能检测来电"
                        },
                        value = if (hasPhonePermission) "已授权" else "去授权",
                        icon = Icons.Rounded.Security,
                        onClick = {
                            runCatching {
                                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            }.onFailure {
                                viewModel.postSnackbar("无法打开权限请求，请到系统设置中授权", isError = true)
                                openAppDetails(context)
                            }
                        },
                    )
                    if (!hasCallLogPermission) {
                        RowDivider()
                        SettingActionRow(
                            title = "读取来电号码",
                            subtitle = "授予通话记录权限后可在推送中显示号码",
                            value = "去授权",
                            icon = Icons.Rounded.Security,
                            onClick = {
                                runCatching {
                                    callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                }.onFailure {
                                    viewModel.postSnackbar("无法打开权限请求，请到系统设置中授权", isError = true)
                                    openAppDetails(context)
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoNote("未授予通话记录权限时，系统可能不提供来电号码；来电事件仍会转发，号码会显示为未知。")
                    }
                    RowDivider()
                    FilterListEditor(
                        title = "号码白名单",
                        value = settings.callWhitelist,
                        onCommit = viewModel::setCallWhitelist,
                        placeholder = "留空表示不限制；例如 +8613800138000",
                    )
                    Spacer(Modifier.height(10.dp))
                    FilterListEditor(
                        title = "号码黑名单",
                        value = settings.callBlacklist,
                        onCommit = viewModel::setCallBlacklist,
                        placeholder = "这些号码的来电不会推送",
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
            Spacer(Modifier.height(10.dp))
            InfoNote("电话事件使用当前默认推送通道和路由规则；不会读取通话内容，也不会自动拨号。")
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
                    onClick = { templateMode = "notification" },
                )
                if (settings.notificationForwardEnabled) {
                    RowDivider()
                    SettingSwitchRow(
                        title = "所有应用",
                        checked = settings.notificationAllApps,
                        onCheckedChange = viewModel::setNotificationAllApps,
                        subtitle = if (settings.notificationAllApps) {
                            "转发所有已安装应用的通知"
                        } else {
                            "只转发下方选中的应用"
                        },
                    )
                    if (!settings.notificationAllApps) {
                        RowDivider()
                        SettingActionRow(
                            title = "应用白名单",
                            subtitle = "只转发选中应用的通知",
                            value = "${settings.notificationSelectedPackages.size} 个",
                            icon = Icons.Rounded.Badge,
                            onClick = { appPickerMode = "whitelist" },
                        )
                    }
                    RowDivider()
                    SettingActionRow(
                        title = "应用黑名单",
                        subtitle = "这些应用的通知永远不转发",
                        value = "${settings.notificationBlacklistPackages.size} 个",
                        icon = Icons.Rounded.Badge,
                        onClick = { appPickerMode = "blacklist" },
                    )
                    RowDivider()
                    SettingSwitchRow(
                        title = "忽略常驻通知",
                        checked = settings.notificationIgnoreOngoing,
                        onCheckedChange = viewModel::setNotificationIgnoreOngoing,
                        subtitle = "忽略前台服务、下载进度等持续更新的通知",
                    )
                    RowDivider()
                    SettingActionRow(
                        title = "通知使用权",
                        subtitle = if (notificationAccess) {
                            "已获得通知访问权限"
                        } else {
                            "需要通知访问权限才能捕获应用通知"
                        },
                        value = if (notificationAccess) "已授权" else "去授权",
                        icon = Icons.Rounded.Security,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                                )
                            }.onFailure {
                                viewModel.postSnackbar("无法打开通知使用权设置，请到系统设置中手动开启", isError = true)
                                openAppDetails(context)
                            }
                        },
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
            Spacer(Modifier.height(10.dp))
            InfoNote("应用通知转发依赖系统的通知使用权；关闭授权后服务会自动停止捕获。通知内容只发送到你配置的推送通道，不经过应用服务器。")
        }
    }

    appPickerMode?.let { mode ->
        AppPickerDialog(
            title = if (mode == "blacklist") "选择应用黑名单" else "选择应用白名单",
            selected = if (mode == "blacklist") {
                settings.notificationBlacklistPackages.toSet()
            } else {
                settings.notificationSelectedPackages.toSet()
            },
            onDismiss = { appPickerMode = null },
            onConfirm = { packages ->
                if (mode == "blacklist") {
                    viewModel.setNotificationBlacklistPackages(packages.toList())
                } else {
                    viewModel.setNotificationSelectedPackages(packages.toList())
                }
                appPickerMode = null
            },
        )
    }

    templateMode?.let { mode ->
        EventTemplateDialog(
            mode = mode,
            initialTitle = if (mode == "call") settings.callTitleTemplate else settings.notificationTitleTemplate,
            initialBody = if (mode == "call") settings.callBodyTemplate else settings.notificationBodyTemplate,
            onDismiss = { templateMode = null },
            onSave = { title, body ->
                if (mode == "call") {
                    viewModel.setCallTemplates(title, body)
                } else {
                    viewModel.setNotificationTemplates(title, body)
                }
                templateMode = null
            },
        )
    }
}

@Composable
private fun EventTemplateDialog(
    mode: String,
    initialTitle: String,
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    val tokens = if (mode == "call") {
        "{type} {sender} {time} {device}"
    } else {
        "{app} {title} {body} {time} {device}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "call") "电话推送模板" else "应用通知推送模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "可用变量：$tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("推送标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("推送正文") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AppPickerDialog(
    title: String,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(selected) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            activities.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString()
                InstalledApp(label, packageName)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
        apps = loaded
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (loading) {
                Text("正在读取应用列表…")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.packageName in chosen,
                                onCheckedChange = { checked ->
                                    chosen = if (checked) chosen + app.packageName else chosen - app.packageName
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
