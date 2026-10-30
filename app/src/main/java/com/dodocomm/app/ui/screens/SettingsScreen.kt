@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dodocomm.app.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.BuildConfig
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.core.system.OemGuides
import com.dodocomm.app.core.system.SystemNavigator
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.ConfirmDialog
import com.dodocomm.app.ui.components.FilterListEditor
import com.dodocomm.app.ui.components.IconBubble
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.ScreenTitle
import com.dodocomm.app.ui.components.SecretField
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.SettingTextField
import com.dodocomm.app.ui.components.TemplateField
import com.dodocomm.app.ui.model.PermissionState
import com.dodocomm.app.ui.model.UpdateState
import com.dodocomm.app.ui.model.TestPushState
import com.dodocomm.app.ui.theme.FieldShape

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateGuide: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testPushState by viewModel.testPushState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateDialogVisible by viewModel.updateDialogVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val deviceLabel = remember { OemGuides.detect().deviceLabel }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 44.dp),
    ) {
        item(key = "title", contentType = "header") {
            ScreenTitle(title = "设置", subtitle = "修改后立即生效，自动保存")
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "bark_header", contentType = "header") {
            StickySectionHeader("Bark 服务器")
        }
        item(key = "bark_card", contentType = "card") {
            BarkServerCard(
                settings = settings,
                testPushState = testPushState,
                onServerUrlCommit = viewModel::setServerUrl,
                onDeviceKeyCommit = viewModel::setDeviceKey,
                onTest = viewModel::sendTestPush,
            )
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "push_header", contentType = "header") {
            StickySectionHeader("推送样式")
        }
        item(key = "push_card", contentType = "card") {
            PushStyleCard(settings = settings, viewModel = viewModel)
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "rules_header", contentType = "header") {
            StickySectionHeader("转发规则")
        }
        item(key = "rules_note", contentType = "note") {
            InfoNote(
                text = "白名单非空时只转发列表中的号码，白名单优先于黑名单；号码按后缀匹配（如 138 可匹配 +86138…），关键词命中即过滤。",
            )
            Spacer(Modifier.height(10.dp))
        }
        item(key = "rules_card", contentType = "card") {
            ForwardingRulesCard(settings = settings, viewModel = viewModel)
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "reliability_header", contentType = "header") {
            StickySectionHeader("后台与可靠性")
        }
        item(key = "reliability_card", contentType = "card") {
            ReliabilityCard(
                settings = settings,
                permissions = permissions,
                deviceLabel = deviceLabel,
                onToggleForegroundService = viewModel::setForegroundServiceEnabled,
                onToggleAutoStart = viewModel::setAutoStartOnBoot,
                onToggleHideFromRecents = viewModel::setHideFromRecents,
                onOpenGuide = onNavigateGuide,
                onOpenBatterySettings = { SystemNavigator.requestIgnoreBatteryOptimizations(context) },
            )
            Spacer(Modifier.height(10.dp))
        }
        item(key = "reliability_note", contentType = "note") {
            InfoNote(
                text = "关闭常驻通知栏会停用前台服务，改为短信广播 + 每 15 分钟定时补发；后台可靠性会下降，但不需要常驻通知。后台隐藏只影响最近任务列表。",
            )
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "data_header", contentType = "header") {
            StickySectionHeader("数据")
        }
        item(key = "data_card", contentType = "card") {
            DataCard(
                settings = settings,
                recordCount = records.size,
                onSetRetention = viewModel::setRetentionDays,
                onSetMaxRecords = viewModel::setMaxRecords,
                onPruneNow = viewModel::pruneNow,
            )
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "appearance_header", contentType = "header") {
            StickySectionHeader("外观")
        }
        item(key = "appearance_card", contentType = "card") {
            AppearanceCard(settings = settings, viewModel = viewModel)
            Spacer(Modifier.height(20.dp))
        }

        stickyHeader(key = "about_header", contentType = "header") {
            StickySectionHeader("关于")
        }
        item(key = "about_card", contentType = "card") {
            AboutCard(
                recordCount = records.size,
                autoUpdateCheck = settings.autoUpdateCheck,
                updateState = updateState,
                onOpenGuide = onNavigateGuide,
                onClearHistory = { showClearDialog = true },
                onCheckUpdates = {
                    if (updateState is UpdateState.Available || updateState is UpdateState.Error) {
                        viewModel.showUpdateDialog()
                    } else {
                        viewModel.checkForUpdates()
                    }
                },
                onToggleAutoUpdate = viewModel::setAutoUpdateCheck,
            )
            Spacer(Modifier.height(20.dp))
        }

        item(key = "footer", contentType = "footer") {
            Text(
                text = "本地运行 · 无账号 · 无广告",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "清空转发记录？",
            message = "本地保存的 ${records.size} 条记录会被全部删除，无法恢复。",
            confirmLabel = "清空",
            destructive = true,
            onConfirm = { viewModel.clearHistory() },
            onDismiss = { showClearDialog = false },
        )
    }

    if (updateDialogVisible) {
        UpdateDialog(
            state = updateState,
            onDownload = viewModel::downloadAndInstallUpdate,
            onDismiss = viewModel::dismissUpdateDialog,
        )
    }
}

/** Section label pinned to the top while its card scrolls underneath. */
@Composable
private fun StickySectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 10.dp, bottom = 10.dp),
    ) {
        SectionHeader(title)
    }
}

// ---------------------------------------------------------------------------
// Bark server
// ---------------------------------------------------------------------------

@Composable
private fun BarkServerCard(
    settings: AppSettings,
    testPushState: TestPushState,
    onServerUrlCommit: (String) -> Unit,
    onDeviceKeyCommit: (String) -> Unit,
    onTest: () -> Unit,
) {
    SectionCard {
        SettingTextField(
            value = settings.serverUrl,
            onCommit = onServerUrlCommit,
            label = "服务器地址",
            placeholder = "https://api.day.app",
            supportingText = "支持自建 Bark，局域网 http 地址同样可用",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(14.dp))
        SecretField(
            value = settings.deviceKey,
            onCommit = onDeviceKeyCommit,
            label = "Device Key",
            placeholder = "在 Bark App 的服务器页面查看",
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = onTest,
                enabled = testPushState !is TestPushState.Loading,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                if (testPushState is TestPushState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在连接…")
                } else {
                    Text("测试连接")
                }
            }
            Spacer(Modifier.width(12.dp))
            when (testPushState) {
                is TestPushState.Success -> InlineResult(
                    text = "连接成功",
                    success = true,
                )

                is TestPushState.Failure -> InlineResult(
                    text = "连接失败",
                    success = false,
                )

                else -> Text(
                    text = "发一条测试推送验证配置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val message = when (testPushState) {
            is TestPushState.Success -> testPushState.message
            is TestPushState.Failure -> testPushState.message
            else -> null
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (testPushState is TestPushState.Success) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun InlineResult(text: String, success: Boolean) {
    val color = if (success) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(5.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

// ---------------------------------------------------------------------------
// Push style
// ---------------------------------------------------------------------------

@Composable
private fun PushStyleCard(settings: AppSettings, viewModel: AppViewModel) {
    SectionCard {
        TemplateField(
            value = settings.titleTemplate,
            onCommit = viewModel::setTitleTemplate,
        )
        Spacer(Modifier.height(16.dp))
        SettingTextField(
            value = settings.pushGroup,
            onCommit = viewModel::setPushGroup,
            label = "通知分组",
            placeholder = "短信",
            supportingText = "相同分组的推送会在通知中心折叠",
        )
        Spacer(Modifier.height(14.dp))
        SettingTextField(
            value = settings.pushSound,
            onCommit = viewModel::setPushSound,
            label = "通知声音",
            placeholder = "默认",
            supportingText = "Bark 声音名，如 minuet、alarm；留空使用默认",
        )
        Spacer(Modifier.height(14.dp))
        PushLevelRow(selected = settings.pushLevel, onSelect = viewModel::setPushLevel)
        Spacer(Modifier.height(14.dp))
        SettingTextField(
            value = settings.pushIcon,
            onCommit = viewModel::setPushIcon,
            label = "图标 URL",
            placeholder = "https://…/icon.png",
        )
        Spacer(Modifier.height(14.dp))
        SettingTextField(
            value = settings.pushUrl,
            onCommit = viewModel::setPushUrl,
            label = "点击跳转 URL",
            placeholder = "https://…（可选）",
        )
        Spacer(Modifier.height(6.dp))
        RowDivider()
        SettingSwitchRow(
            title = "归档推送",
            checked = settings.isArchive,
            onCheckedChange = viewModel::setArchive,
            subtitle = "在 Bark 历史中保留这条推送",
        )
        RowDivider()
        CopyModeRow(selected = settings.copyMode, onSelect = viewModel::setCopyMode)
        RowDivider()
        SettingSwitchRow(
            title = "显示角标",
            checked = settings.badgeEnabled,
            onCheckedChange = viewModel::setBadge,
            subtitle = "Bark App 图标显示未读数",
        )
    }
}

@Composable
private fun PushLevelRow(selected: PushLevel, onSelect: (PushLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingActionRow(
            title = "推送级别",
            subtitle = "决定 iPhone 如何提醒这条推送",
            value = selected.label,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PushLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        onSelect(level)
                        expanded = false
                    },
                    trailingIcon = {
                        if (level == selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Forwarding rules
// ---------------------------------------------------------------------------

@Composable
private fun ForwardingRulesCard(settings: AppSettings, viewModel: AppViewModel) {
    SectionCard {
        FilterListEditor(
            title = "发送者白名单",
            value = settings.senderWhitelist,
            onCommit = viewModel::setSenderWhitelist,
            placeholder = "例如 95588、+8613800138000",
        )
        Spacer(Modifier.height(16.dp))
        FilterListEditor(
            title = "发送者黑名单",
            value = settings.senderBlacklist,
            onCommit = viewModel::setSenderBlacklist,
            placeholder = "这些号码的短信不会被转发",
        )
        Spacer(Modifier.height(16.dp))
        FilterListEditor(
            title = "关键词黑名单",
            value = settings.keywordBlacklist,
            onCommit = viewModel::setKeywordBlacklist,
            placeholder = "例如 验证码、退订、广告",
        )
        Spacer(Modifier.height(8.dp))
        RowDivider()
        SettingSwitchRow(
            title = "记录被过滤短信",
            checked = settings.recordFilteredMessages,
            onCheckedChange = viewModel::setRecordFilteredMessages,
            subtitle = "被规则拦截的短信也写入转发记录",
        )
    }
}

// ---------------------------------------------------------------------------
// Background & reliability
// ---------------------------------------------------------------------------

@Composable
private fun ReliabilityCard(
    settings: AppSettings,
    permissions: PermissionState,
    deviceLabel: String,
    onToggleForegroundService: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleHideFromRecents: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    SectionCard {
        SettingSwitchRow(
            title = "常驻通知栏",
            checked = settings.foregroundServiceEnabled,
            onCheckedChange = onToggleForegroundService,
            subtitle = if (settings.foregroundServiceEnabled) {
                "开启前台服务，通知栏显示常驻通知"
            } else {
                "已关闭：不显示常驻通知，仅靠短信广播 + 定时补发"
            },
            icon = Icons.Rounded.NotificationsActive,
        )
        RowDivider()
        SettingSwitchRow(
            title = "后台隐藏",
            checked = settings.hideFromRecents,
            onCheckedChange = onToggleHideFromRecents,
            subtitle = "离开 App 后从最近任务中移除（桌面图标仍保留，转发不受影响）",
            icon = Icons.Rounded.VisibilityOff,
        )
        RowDivider()
        SettingSwitchRow(
            title = "开机自动恢复",
            checked = settings.autoStartOnBoot,
            onCheckedChange = onToggleAutoStart,
            subtitle = "重启手机后自动恢复转发服务",
            icon = Icons.Rounded.RestartAlt,
        )
        RowDivider()
        SettingActionRow(
            title = "电池优化",
            subtitle = if (permissions.batteryOptimizationsIgnored) {
                "已豁免，系统不会冻结后台服务"
            } else {
                "未豁免，熄屏后可能被系统限制"
            },
            value = if (permissions.batteryOptimizationsIgnored) "已豁免" else "去设置",
            icon = Icons.Rounded.BatterySaver,
            onClick = onOpenBatterySettings,
        )
        RowDivider()
        SettingActionRow(
            title = "保活向导",
            subtitle = "针对 $deviceLabel 的分步设置说明",
            icon = Icons.Rounded.Bolt,
            onClick = onOpenGuide,
        )
    }
}

private fun updateSubtitle(state: UpdateState): String = when (state) {
    is UpdateState.Checking -> "正在连接 GitHub Releases…"
    is UpdateState.UpToDate -> "已是最新版本"
    is UpdateState.Available -> "发现新版本 v${state.info.versionName}，点击查看"
    is UpdateState.Downloading -> "正在下载更新：${state.progress}%"
    is UpdateState.Error -> state.message
    UpdateState.Idle -> "当前版本，点击检查 GitHub Releases"
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本 v${state.info.versionName}") },
            text = {
                Column {
                    Text(
                        text = "安装包大小：${formatSize(state.info.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.info.changelog.take(1200))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "更新会保留本地设置与转发记录；安装包校验 SHA-256 与签名后才交给系统安装器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text("下载并安装") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column {
                    Text("v${state.info.versionName} · ${state.progress}%")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("更新失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        )

        else -> Unit
    }
}

private fun retentionLabel(days: Int): String = if (days <= 0) "永久" else "${days} 天"

private fun maxRecordsLabel(max: Int): String = when (max) {
    500 -> "500 条"
    1000 -> "1000 条"
    5000 -> "5000 条"
    else -> "${max} 条"
}

@Composable
private fun CopyModeRow(selected: CopyMode, onSelect: (CopyMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingActionRow(
            title = "自动复制",
            subtitle = "Bark 在 iPhone 上收到后复制到剪贴板",
            value = selected.label,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CopyMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                    trailingIcon = {
                        if (mode == selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DataCard(
    settings: AppSettings,
    recordCount: Int,
    onSetRetention: (Int) -> Unit,
    onSetMaxRecords: (Int) -> Unit,
    onPruneNow: () -> Unit,
) {
    var retentionExpanded by remember { mutableStateOf(false) }
    var maxExpanded by remember { mutableStateOf(false) }
    SectionCard {
        Box {
            SettingActionRow(
                title = "记录保留期限",
                subtitle = "超过期限的记录会由后台维护任务自动清理",
                value = retentionLabel(settings.retentionDays),
                onClick = { retentionExpanded = true },
            )
            DropdownMenu(expanded = retentionExpanded, onDismissRequest = { retentionExpanded = false }) {
                listOf(0, 7, 30, 90).forEach { days ->
                    DropdownMenuItem(
                        text = { Text(if (days == 0) "永久保留" else "$days 天") },
                        onClick = {
                            onSetRetention(days)
                            retentionExpanded = false
                        },
                        trailingIcon = {
                            if (settings.retentionDays == days) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }
        RowDivider()
        Box {
            SettingActionRow(
                title = "最多保留",
                subtitle = "本地最多保存的记录条数（当前 $recordCount 条）",
                value = maxRecordsLabel(settings.maxRecords),
                onClick = { maxExpanded = true },
            )
            DropdownMenu(expanded = maxExpanded, onDismissRequest = { maxExpanded = false }) {
                listOf(500, 1000, 5000).forEach { max ->
                    DropdownMenuItem(
                        text = { Text("$max 条") },
                        onClick = {
                            onSetMaxRecords(max)
                            maxExpanded = false
                        },
                        trailingIcon = {
                            if (settings.maxRecords == max) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }
        RowDivider()
        SettingActionRow(
            title = "立即清理",
            subtitle = "按当前保留策略删除过期/超量记录",
            icon = Icons.Rounded.Delete,
            onClick = onPruneNow,
        )
    }
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceCard(settings: AppSettings, viewModel: AppViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = Icons.Rounded.Palette, size = 34, iconSize = 18)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "主题模式",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "切换后立即生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = ThemeMode.entries
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Text(text = mode.label, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RowDivider()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingSwitchRow(
                title = "跟随系统取色",
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
                subtitle = "使用壁纸提取的品牌色",
            )
        } else {
            SettingSwitchRow(
                title = "跟随系统取色",
                checked = false,
                onCheckedChange = {},
                subtitle = "需要 Android 12 及以上系统",
                enabled = false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

@Composable
private fun AboutCard(
    recordCount: Int,
    autoUpdateCheck: Boolean,
    updateState: UpdateState,
    onOpenGuide: () -> Unit,
    onClearHistory: () -> Unit,
    onCheckUpdates: () -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(icon = Icons.AutoMirrored.Rounded.HelpOutline, size = 34, iconSize = 18)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "版本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "嘟嘟通讯 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RowDivider()
        SettingActionRow(
            title = "检查更新",
            subtitle = updateSubtitle(updateState),
            value = when (updateState) {
                is UpdateState.Available -> "v${updateState.info.versionName}"
                is UpdateState.Checking -> "检查中…"
                is UpdateState.Downloading -> "${updateState.progress}%"
                else -> "检查"
            },
            icon = Icons.Rounded.Refresh,
            onClick = onCheckUpdates,
        )
        RowDivider()
        SettingSwitchRow(
            title = "自动检查更新",
            checked = autoUpdateCheck,
            onCheckedChange = onToggleAutoUpdate,
            subtitle = "成功时每 24 小时一次；失败按 1h→2h→…→24h 指数退避",
            icon = Icons.Rounded.Refresh,
        )
        RowDivider()
        SettingActionRow(
            title = "使用帮助",
            subtitle = "服务器配置与后台保活说明",
            icon = Icons.AutoMirrored.Rounded.HelpOutline,
            onClick = onOpenGuide,
        )
        RowDivider()
        SettingActionRow(
            title = "清空转发记录",
            subtitle = "删除本地全部 $recordCount 条记录",
            icon = Icons.Rounded.Delete,
            destructive = true,
            onClick = onClearHistory,
        )
        Spacer(Modifier.height(12.dp))
        InfoNote(
            text = "图标与吉祥物「嘟嘟可」灵感来自原神「嘟嘟通讯」活动，是非官方同人作品，与米哈游无关。",
        )
        Spacer(Modifier.height(8.dp))
        InfoNote(
            text = "短信内容仅发送到你配置的 Bark 服务器，不经过任何第三方；本地只保留最近 500 条记录，随时可以清空。",
        )
    }
}
