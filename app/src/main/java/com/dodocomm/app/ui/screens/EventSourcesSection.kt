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

@Composable
fun EventSourcesSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var permissionEpoch by remember { mutableStateOf(0) }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
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
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            phonePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG,
                                ),
                            )
                        },
                    )
                    if (!hasCallLogPermission) {
                        Spacer(Modifier.height(8.dp))
                        InfoNote("未授予通话记录权限时，系统可能不提供来电号码；来电事件仍会转发，号码会显示为未知。")
                    }
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
                            title = "选择应用",
                            subtitle = "只转发选中应用的通知",
                            value = "${settings.notificationSelectedPackages.size} 个",
                            icon = Icons.Rounded.Badge,
                            onClick = { showAppPicker = true },
                        )
                    }
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
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
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

    if (showAppPicker) {
        AppPickerDialog(
            selected = settings.notificationSelectedPackages.toSet(),
            onDismiss = { showAppPicker = false },
            onConfirm = { packages ->
                viewModel.setNotificationSelectedPackages(packages.toList())
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun AppPickerDialog(
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
        title = { Text("选择要转发的应用") },
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
