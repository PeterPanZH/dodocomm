package com.dodocomm.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.FilterListEditor
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CallForwardingRulesCard(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
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
        }
        InfoNote("黑名单优先于白名单；号码按数字后缀匹配，与短信发送者规则一致。")
    }
}

@Composable
fun AppNotificationRulesCard(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var pickerMode by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard {
            SettingSwitchRow(
                title = "转发所有应用",
                checked = settings.notificationAllApps,
                onCheckedChange = viewModel::setNotificationAllApps,
                subtitle = "关闭后只转发应用白名单中的应用",
            )
            RowDivider()
            SettingActionRow(
                title = "应用白名单",
                subtitle = "只转发这些应用的通知",
                value = "${settings.notificationSelectedPackages.size} 个",
                onClick = { pickerMode = "whitelist" },
            )
            RowDivider()
            SettingActionRow(
                title = "应用黑名单",
                subtitle = "这些应用的通知永远不转发",
                value = "${settings.notificationBlacklistPackages.size} 个",
                onClick = { pickerMode = "blacklist" },
            )
        }
        InfoNote("黑名单优先于白名单。应用列表只用于通知转发，不会读取通知以外的数据。")
    }

    pickerMode?.let { mode ->
        AppPickerDialog(
            title = if (mode == "blacklist") "选择应用黑名单" else "选择应用白名单",
            selected = if (mode == "blacklist") {
                settings.notificationBlacklistPackages.toSet()
            } else {
                settings.notificationSelectedPackages.toSet()
            },
            onDismiss = { pickerMode = null },
            onConfirm = { packages ->
                if (mode == "blacklist") {
                    viewModel.setNotificationBlacklistPackages(packages.toList())
                } else {
                    viewModel.setNotificationSelectedPackages(packages.toList())
                }
                pickerMode = null
            },
        )
    }
}

internal data class InstalledApp(val label: String, val packageName: String)

@Composable
internal fun AppPickerDialog(
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
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            activities.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                InstalledApp(resolveInfo.loadLabel(packageManager).toString(), packageName)
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
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
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
