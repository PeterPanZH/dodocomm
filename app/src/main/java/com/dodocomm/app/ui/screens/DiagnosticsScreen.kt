@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import com.dodocomm.app.ui.i18n.AppStrings
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.diagnostics.DiagnosticAction
import com.dodocomm.app.core.diagnostics.DiagnosticGroup
import com.dodocomm.app.core.diagnostics.DiagnosticItem
import com.dodocomm.app.core.diagnostics.DiagnosticReport
import com.dodocomm.app.core.diagnostics.DiagnosticStatus
import com.dodocomm.app.core.diagnostics.DiagnosticsChecker
import com.dodocomm.app.core.diagnostics.DiagnosticsInput
import com.dodocomm.app.core.system.SystemNavigator
import com.dodocomm.app.ui.model.UpdateState
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.IconBubble
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.Text

@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenRetryHistory: () -> Unit,
    onOpenSettingsSection: (SettingsSection) -> Unit,
    onOpenCrashLogs: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var networkAvailable by remember { mutableStateOf(isNetworkAvailable(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshSystemState() }

    val updateAvailable = updateState is UpdateState.Available
    val report = remember(
        settings,
        permissions,
        stats,
        serviceRunning,
        networkAvailable,
        updateAvailable,
        refreshKey,
    ) {
        DiagnosticsChecker.check(
            DiagnosticsInput(
                settings = settings,
                permissions = permissions,
                stats = stats,
                serviceRunning = serviceRunning,
                networkAvailable = networkAvailable,
                updateAvailable = updateAvailable,
            ),
        )
    }

    fun action(item: DiagnosticItem) {
        when (item.action) {
            DiagnosticAction.RECEIVE_SMS ->
                permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            DiagnosticAction.POST_NOTIFICATIONS ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            DiagnosticAction.READ_PHONE_STATE ->
                permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            DiagnosticAction.BATTERY_OPTIMIZATION ->
                SystemNavigator.requestIgnoreBatteryOptimizations(context)
            DiagnosticAction.OPEN_RELIABILITY ->
                onOpenSettingsSection(SettingsSection.RELIABILITY)
            DiagnosticAction.OPEN_GUIDE -> onOpenGuide()
            DiagnosticAction.OPEN_CHANNELS -> onOpenChannels()
            DiagnosticAction.OPEN_ROUTING -> onOpenRouting()
            DiagnosticAction.OPEN_HISTORY_RETRY -> onOpenRetryHistory()
            DiagnosticAction.OPEN_UPDATE ->
                onOpenSettingsSection(SettingsSection.ABOUT)
            null -> Unit
        }
    }

    fun copyReport() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("DodoComm 运行诊断", report.toShareText()))
        viewModel.postSnackbar("诊断报告已复制")
    }

    fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report.toShareText())
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享诊断报告"))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = AppStrings.translate("返回"),
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("运行诊断", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "检查权限、后台、通道和送达状态",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    networkAvailable = isNetworkAvailable(context)
                    refreshKey++
                    viewModel.onResume()
                },
            ) {
                Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("重新检测")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "summary") { SummaryCard(report) }
            DiagnosticGroup.entries.forEach { group ->
                val items = report.items.filter { it.group == group }
                if (items.isNotEmpty()) {
                    item(key = group.name) {
                        Column {
                            SectionHeader(group.label)
                            SectionCard {
                                items.forEachIndexed { index, item ->
                                    if (index > 0) RowDivider()
                                    DiagnosticRow(item) { action(item) }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "crash_logs") {
                TextButton(onClick = onOpenCrashLogs) {
                    Text("查看本地崩溃日志")
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = ::copyReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制报告")
                    }
                    Button(
                        onClick = ::shareReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("分享")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(report: DiagnosticReport) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                icon = if (report.healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                size = 48,
                iconSize = 24,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (report.healthy) "一切正常，嘟嘟可随时待命" else "有需要留意的地方",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(item: DiagnosticItem, onAction: () -> Unit) {
    val (icon, tint) = when (item.status) {
        DiagnosticStatus.PASS -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
        DiagnosticStatus.WARN -> Icons.Rounded.Warning to MaterialTheme.colorScheme.tertiary
        DiagnosticStatus.FAIL -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.action != null) {
            TextButton(onClick = onAction) {
                Text(item.actionLabel ?: "去处理", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(false)
