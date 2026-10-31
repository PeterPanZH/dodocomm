@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.dodocomm.app.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text as MaterialText
import com.dodocomm.app.ui.components.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.dodocomm.app.BuildConfig
import com.dodocomm.app.core.export.ExportFormat
import com.dodocomm.app.core.export.ExportRange
import com.dodocomm.app.core.settings.AppLanguage
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.PushLevel
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.core.system.OemGuides
import com.dodocomm.app.core.system.SystemNavigator
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.ConfirmDialog
import com.dodocomm.app.ui.components.FilterListEditor
import com.dodocomm.app.ui.components.IconBubble
import com.dodocomm.app.ui.components.CopyModeRow
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.MarkdownText
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
import java.time.LocalDate

enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val route: String,
) {
    OVERVIEW("设置", "按类别管理所有设置", "overview"),
    PUSH("推送", "推送内容、推送通道与路由规则", "push"),
    RULES("转发规则", "白名单、黑名单与关键词过滤", "rules"),
    PRIVACY("隐私与安全", "应用锁与本地数据保护", "privacy"),
    RELIABILITY("后台与可靠性", "常驻通知、开机恢复与电池优化", "reliability"),
    DATA("数据", "记录保留、导出与加密备份", "data"),
    APPEARANCE("外观", "主题、语言与动态取色", "appearance"),
    ABOUT("关于", "版本更新、帮助与项目信息", "about"),
}

@Composable
private fun SettingsSubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsOverviewScreen(
    settings: AppSettings,
    records: List<com.dodocomm.app.core.model.ForwardRecord>,
    updateState: UpdateState,
    onNavigateSection: (SettingsSection) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 44.dp),
    ) {
        item(key = "overview_title") {
            ScreenTitle(title = "设置")
            Spacer(Modifier.height(20.dp))
        }

        item(key = "overview_connection") {
            SectionCard {
                OverviewRow(
                    title = "推送",
                    subtitle = "共用模板、推送通道与路由规则",
                    value = "${settings.channels.count { it.enabled }} 个通道",
                    icon = Icons.Rounded.NotificationsActive,
                    onClick = { onNavigateSection(SettingsSection.PUSH) },
                )
                RowDivider()
                OverviewRow(
                    title = "转发规则",
                    subtitle = "白名单、黑名单与关键词过滤",
                    value = if (settings.hasFilters) "已启用" else "未设置",
                    icon = Icons.Rounded.FilterAlt,
                    onClick = { onNavigateSection(SettingsSection.RULES) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "overview_security") {
            SectionCard {
                OverviewRow(
                    title = "隐私与安全",
                    subtitle = "应用锁、自动锁定与生物识别",
                    value = if (settings.appLockEnabled) "已开启" else "未开启",
                    icon = Icons.Rounded.Lock,
                    onClick = { onNavigateSection(SettingsSection.PRIVACY) },
                )
                RowDivider()
                OverviewRow(
                    title = "后台与可靠性",
                    subtitle = "常驻通知、开机恢复、电池优化与保活向导",
                    value = if (settings.foregroundServiceEnabled) "常驻通知" else "广播模式",
                    icon = Icons.Rounded.Bolt,
                    onClick = { onNavigateSection(SettingsSection.RELIABILITY) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "overview_data") {
            SectionCard {
                OverviewRow(
                    title = "数据",
                    subtitle = "记录保留策略、导出与加密配置备份",
                    value = "${records.size} 条记录",
                    icon = Icons.Rounded.Storage,
                    onClick = { onNavigateSection(SettingsSection.DATA) },
                )
                RowDivider()
                OverviewRow(
                    title = "外观",
                    subtitle = "主题模式、界面语言与动态取色",
                    value = settings.themeMode.label,
                    icon = Icons.Rounded.Palette,
                    onClick = { onNavigateSection(SettingsSection.APPEARANCE) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "overview_about") {
            SectionCard {
                OverviewRow(
                    title = "关于",
                    subtitle = "版本更新、帮助与项目信息",
                    value = if (updateState is UpdateState.Available) {
                        "有新版本 v${updateState.info.versionName}"
                    } else {
                        "v${BuildConfig.VERSION_NAME}"
                    },
                    icon = Icons.Rounded.Info,
                    onClick = { onNavigateSection(SettingsSection.ABOUT) },
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "本地运行 · 无账号 · 无广告",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OverviewRow(
    title: String,
    subtitle: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    SettingActionRow(
        title = title,
        subtitle = subtitle,
        value = value,
        icon = icon,
        onClick = onClick,
    )
}

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateGuide: () -> Unit,
    onNavigateTemplate: () -> Unit,
    onNavigateChannels: () -> Unit = {},
    onNavigateRouting: () -> Unit = {},
    section: SettingsSection = SettingsSection.OVERVIEW,
    onNavigateSection: (SettingsSection) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateDialogVisible by viewModel.updateDialogVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var pinDialogMode by rememberSaveable { mutableStateOf<String?>(null) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var exportFormatName by rememberSaveable { mutableStateOf(ExportFormat.CSV.name) }
    var exportRangeName by rememberSaveable { mutableStateOf(ExportRange.ALL.name) }
    var exportMasked by rememberSaveable { mutableStateOf(true) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportRecords(
                uri = uri,
                format = ExportFormat.valueOf(exportFormatName),
                range = ExportRange.valueOf(exportRangeName),
                masked = exportMasked,
            )
        }
    }
    var showBackupExportDialog by rememberSaveable { mutableStateOf(false) }
    var showBackupImportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingBackupPassword by rememberSaveable { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri, pendingBackupPassword)
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showBackupImportDialog = true
        }
    }
    val deviceLabel = remember { OemGuides.detect().deviceLabel }
    val biometricAvailable = remember {
        runCatching {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    if (section == SettingsSection.OVERVIEW) {
        SettingsOverviewScreen(
            settings = settings,
            records = records,
            updateState = updateState,
            onNavigateSection = onNavigateSection,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 44.dp),
    ) {
        item(key = "subpage_title", contentType = "header") {
            SettingsSubpageHeader(
                title = section.title,
                subtitle = section.subtitle,
                onBack = onBack,
            )
            Spacer(Modifier.height(18.dp))
        }

        if (section == SettingsSection.PUSH) {
            item(key = "push_card", contentType = "card") {
                PushStyleCard(
                    settings = settings,
                    viewModel = viewModel,
                    onEditTemplate = onNavigateTemplate,
                    onNavigateChannels = onNavigateChannels,
                    onNavigateRouting = onNavigateRouting,
                )
            }
        }

        if (section == SettingsSection.RULES) {
            item(key = "rules_note", contentType = "note") {
                InfoNote(
                    text = "白名单非空时只转发列表中的号码，白名单优先于黑名单；号码按后缀匹配（如 138 可匹配 +86138…），关键词命中即过滤。",
                )
                Spacer(Modifier.height(10.dp))
            }
            item(key = "rules_card", contentType = "card") {
                ForwardingRulesCard(settings = settings, viewModel = viewModel)
            }
        }

        if (section == SettingsSection.PRIVACY) {
            item(key = "privacy_card", contentType = "card") {
                PrivacyCard(
                    settings = settings,
                    biometricAvailable = biometricAvailable,
                    onToggleAppLock = { enabled ->
                        pinDialogMode = if (enabled) "setup" else "disable"
                    },
                    onChangePin = { pinDialogMode = "change" },
                    onSetAutoLockMinutes = viewModel::setAutoLockMinutes,
                    onToggleBiometric = viewModel::setBiometricUnlock,
                )
            }
        }

        if (section == SettingsSection.RELIABILITY) {
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
                Spacer(Modifier.height(16.dp))
            }
            item(key = "retry_card", contentType = "card") {
                RetryPolicyCard(settings = settings, viewModel = viewModel)
            }
        }

        if (section == SettingsSection.DATA) {
            item(key = "data_card", contentType = "card") {
                DataCard(
                    settings = settings,
                    recordCount = records.size,
                    onSetRetention = viewModel::setRetentionDays,
                    onSetMaxRecords = viewModel::setMaxRecords,
                    onPruneNow = viewModel::pruneNow,
                    onExportRecords = { showExportDialog = true },
                    onBackup = { showBackupExportDialog = true },
                    onRestore = {
                        launchSaf(context) {
                            backupImportLauncher.launch(arrayOf("*/*"))
                        }
                    },
                )
            }
        }

        if (section == SettingsSection.APPEARANCE) {
            item(key = "appearance_card", contentType = "card") {
                AppearanceCard(settings = settings, viewModel = viewModel)
            }
        }

        if (section == SettingsSection.ABOUT) {
            item(key = "about_card", contentType = "card") {
                AboutCard(
                    recordCount = records.size,
                    autoUpdateCheck = settings.autoUpdateCheck,
                    updateState = updateState,
                    onOpenGuide = onNavigateGuide,
                    onClearHistory = { showClearDialog = true },
                    onCheckUpdates = {
                        if (updateState is UpdateState.Available ||
                            updateState is UpdateState.Error ||
                            updateState is UpdateState.Ready
                        ) {
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
            onDownloadInBackground = viewModel::downloadUpdateInBackground,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstallReady = viewModel::installReadyUpdate,
            onDismiss = viewModel::dismissUpdateDialog,
        )
    }

    pinDialogMode?.let { mode ->
        AppLockPinScreen(
            mode = mode,
            onDismiss = { pinDialogMode = null },
            onSubmit = { currentPin, newPin, confirmPin ->
                when (mode) {
                    "setup" -> when {
                        !com.dodocomm.app.core.security.AppLock.isValidPin(newPin) -> "PIN 必须为 6 位数字"
                        newPin != confirmPin -> "两次输入的 PIN 不一致"
                        !viewModel.enableAppLock(newPin) -> "PIN 格式无效"
                        else -> null
                    }
                    "change" -> when {
                        !com.dodocomm.app.core.security.AppLock.isValidPin(newPin) -> "PIN 必须为 6 位数字"
                        newPin != confirmPin -> "两次输入的 PIN 不一致"
                        !viewModel.changeAppLockPin(currentPin, newPin) -> "当前 PIN 不正确"
                        else -> null
                    }
                    "disable" -> if (viewModel.disableAppLock(currentPin)) null else "当前 PIN 不正确"
                    else -> null
                }
            },
        )
    }

    if (showExportDialog) {
        ExportRecordsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { format, range, masked ->
                exportFormatName = format.name
                exportRangeName = range.name
                exportMasked = masked
                showExportDialog = false
                val date = LocalDate.now().toString()
                launchSaf(context) {
                    exportLauncher.launch("dodocomm-records-$date.${format.extension}")
                }
            },
        )
    }

    if (showBackupExportDialog) {
        BackupPasswordDialog(
            title = "导出配置备份",
            confirmLabel = "导出",
            onDismiss = { showBackupExportDialog = false },
            onConfirm = { password ->
                pendingBackupPassword = password
                showBackupExportDialog = false
                launchSaf(context) {
                    backupExportLauncher.launch("dodocomm-backup-${LocalDate.now()}.dodocomm-backup")
                }
            },
        )
    }

    if (showBackupImportDialog) {
        BackupImportDialog(
            biometricAvailable = biometricAvailable,
            onDismiss = {
                showBackupImportDialog = false
                pendingImportUri = null
            },
            onPassword = { password ->
                pendingImportUri?.let { viewModel.importBackupWithPassword(it, password) }
                showBackupImportDialog = false
            },
            onBiometric = {
                val uri = pendingImportUri ?: return@BackupImportDialog
                val activity = context as? FragmentActivity
                if (activity == null) {
                    viewModel.importBackupWithDevice(uri)
                } else {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                viewModel.importBackupWithDevice(uri)
                            }
                        },
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("恢复配置备份")
                        .setSubtitle("验证指纹或面容后使用本机密钥")
                        .setNegativeButtonText("取消")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build()
                    runCatching { prompt.authenticate(info) }
                }
                showBackupImportDialog = false
            },
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
                    modifier = Modifier.weight(1f),
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

@Composable
private fun RetryPolicyCard(settings: AppSettings, viewModel: AppViewModel) {
    var attemptsExpanded by remember { mutableStateOf(false) }
    var backoffExpanded by remember { mutableStateOf(false) }
    var hoursExpanded by remember { mutableStateOf(false) }
    SectionCard {
        Box {
            SettingActionRow(
                title = "最大重试次数",
                subtitle = "失败记录超过次数后不再自动重试",
                value = "${settings.maxRetryAttempts} 次",
                onClick = { attemptsExpanded = true },
            )
            DropdownMenu(expanded = attemptsExpanded, onDismissRequest = { attemptsExpanded = false }) {
                listOf(1, 3, 5, 10).forEach { attempts ->
                    DropdownMenuItem(
                        text = { Text("$attempts 次") },
                        onClick = {
                            viewModel.setMaxRetryAttempts(attempts)
                            attemptsExpanded = false
                        },
                    )
                }
            }
        }
        RowDivider()
        Box {
            SettingActionRow(
                title = "重试退避",
                subtitle = "每次失败后的等待时间按指数增长",
                value = when (settings.retryBackoffSeconds) {
                    60 -> "1 分钟"
                    300 -> "5 分钟"
                    else -> "15 秒"
                },
                onClick = { backoffExpanded = true },
            )
            DropdownMenu(expanded = backoffExpanded, onDismissRequest = { backoffExpanded = false }) {
                listOf(15 to "15 秒", 60 to "1 分钟", 300 to "5 分钟").forEach { (seconds, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.setRetryBackoffSeconds(seconds)
                            backoffExpanded = false
                        },
                    )
                }
            }
        }
        RowDivider()
        Box {
            SettingActionRow(
                title = "最大重试时长",
                subtitle = "超过时长的失败记录不再自动重试",
                value = "${settings.retryMaxHours} 小时",
                onClick = { hoursExpanded = true },
            )
            DropdownMenu(expanded = hoursExpanded, onDismissRequest = { hoursExpanded = false }) {
                listOf(1, 6, 24).forEach { hours ->
                    DropdownMenuItem(
                        text = { Text("$hours 小时") },
                        onClick = {
                            viewModel.setRetryMaxHours(hours)
                            hoursExpanded = false
                        },
                    )
                }
            }
        }
        RowDivider()
        SettingSwitchRow(
            title = "仅 Wi-Fi 重试",
            checked = settings.retryOnlyWifi,
            onCheckedChange = viewModel::setRetryOnlyWifi,
            subtitle = "开启后失败重试只会在不计量网络下执行",
        )
    }
}

// ---------------------------------------------------------------------------
// Push style
// ---------------------------------------------------------------------------

@Composable
private fun PushStyleCard(
    settings: AppSettings,
    viewModel: AppViewModel,
    onEditTemplate: () -> Unit,
    onNavigateChannels: () -> Unit,
    onNavigateRouting: () -> Unit,
) {
    SectionCard {
        SettingActionRow(
            title = "推送通道",
            subtitle = "Bark、ntfy、Telegram、Pushover、Email",
            value = "${settings.channels.count { it.enabled }} 个启用",
            icon = Icons.Rounded.NotificationsActive,
            onClick = onNavigateChannels,
        )
        RowDivider()
        SettingActionRow(
            title = "路由规则",
            subtitle = "按发送者、关键词、SIM 或时间段选择通道",
            value = if (settings.routingRules.isEmpty()) "默认" else "${settings.routingRules.size} 条",
            icon = Icons.Rounded.FilterAlt,
            onClick = onNavigateRouting,
        )
        RowDivider()
        SettingActionRow(
            title = "推送内容",
            subtitle = "标题、消息体、设备名称与复制模式",
            value = settings.copyMode.label,
            icon = Icons.Rounded.Edit,
            onClick = onEditTemplate,
        )
    }
}

@Composable
private fun PushLevelRow(selected: PushLevel, onSelect: (PushLevel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingActionRow(
            title = "推送级别",
            subtitle = "决定接收端如何提醒这条推送",
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
    is UpdateState.Ready -> "更新已下载，点击安装"
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
    onDownloadInBackground: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallReady: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本 v${state.info.versionName}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "当前版本 v${BuildConfig.VERSION_NAME} → 新版本 v${state.info.versionName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "安装包大小：${formatSize(state.info.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "本次更新内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    MarkdownText(
                        markdown = state.info.changelog.ifBlank { "更新内容请查看 GitHub Release 页面。" },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "更新会保留本地设置与转发记录；安装包校验 SHA-256 与签名后才交给系统安装器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = onDownloadInBackground) {
                        Text("后台下载")
                    }
                    TextButton(onClick = onDownload) {
                        Text("下载并安装")
                    }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("更新已下载") },
            text = {
                Text("已下载并校验完成，点按安装即可。")
            },
            confirmButton = {
                TextButton(onClick = onInstallReady) { Text("立即安装") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = onCancelDownload,
            title = { Text("正在下载更新") },
            text = {
                Column {
                    Text("v${state.info.versionName} · ${formatSize(state.info.size)} · ${state.progress}%")
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        drawStopIndicator = {},
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDownloadInBackground) {
                    Text("后台下载")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDownload) {
                    Text("取消下载")
                }
            },
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
private fun DataCard(
    settings: AppSettings,
    recordCount: Int,
    onSetRetention: (Int) -> Unit,
    onSetMaxRecords: (Int) -> Unit,
    onPruneNow: () -> Unit,
    onExportRecords: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
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
            title = "备份配置",
            subtitle = "密码短语 + 本机密钥加密，换机可恢复",
            icon = Icons.Rounded.Lock,
            onClick = onBackup,
        )
        RowDivider()
        SettingActionRow(
            title = "恢复配置",
            subtitle = "从 .dodocomm-backup 文件恢复推送设置",
            icon = Icons.Rounded.Refresh,
            onClick = onRestore,
        )
        RowDivider()
        SettingActionRow(
            title = "导出记录",
            subtitle = "CSV / JSON，可选择范围和脱敏",
            icon = Icons.Rounded.Delete,
            onClick = onExportRecords,
        )
        RowDivider()
        SettingActionRow(
            title = "立即清理",
            subtitle = "按当前保留策略删除过期/超量记录",
            icon = Icons.Rounded.Delete,
            onClick = onPruneNow,
        )
    }
}

@Composable
private fun PrivacyCard(
    settings: AppSettings,
    biometricAvailable: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onSetAutoLockMinutes: (Int) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
) {
    var autoLockExpanded by remember { mutableStateOf(false) }
    SectionCard {
        SettingSwitchRow(
            title = "应用锁",
            checked = settings.appLockEnabled,
            onCheckedChange = onToggleAppLock,
            subtitle = "打开 App 需输入 6 位 PIN；禁止截屏与最近任务预览",
            icon = Icons.Rounded.Lock,
        )
        if (settings.appLockEnabled) {
            RowDivider()
            SettingActionRow(
                title = "修改 PIN",
                subtitle = "需要验证当前 PIN",
                value = "",
                icon = Icons.Rounded.Lock,
                onClick = onChangePin,
            )
            RowDivider()
            Box {
                SettingActionRow(
                    title = "自动锁定",
                    subtitle = "离开 App 后多久重新上锁",
                    value = when (settings.autoLockMinutes) {
                        0 -> "立即"
                        1 -> "1 分钟"
                        else -> "5 分钟"
                    },
                    onClick = { autoLockExpanded = true },
                )
                DropdownMenu(expanded = autoLockExpanded, onDismissRequest = { autoLockExpanded = false }) {
                    listOf(0 to "立即", 1 to "1 分钟", 5 to "5 分钟").forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSetAutoLockMinutes(minutes)
                                autoLockExpanded = false
                            },
                            trailingIcon = {
                                if (settings.autoLockMinutes == minutes) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
            if (biometricAvailable) {
                RowDivider()
                SettingSwitchRow(
                    title = "指纹 / 面容解锁",
                    checked = settings.biometricUnlock,
                    onCheckedChange = onToggleBiometric,
                    subtitle = "支持的设备可在 PIN 之外使用生物识别",
                    icon = Icons.Rounded.Lock,
                )
            }
        }
    }
}

@Composable
private fun ExportRecordsDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, ExportRange, Boolean) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.CSV) }
    var range by remember { mutableStateOf(ExportRange.ALL) }
    var masked by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("格式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { item ->
                        FilterChip(
                            selected = format == item,
                            onClick = { format = item },
                            label = { Text(item.name) },
                        )
                    }
                }
                Text("范围", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportRange.entries.forEach { item ->
                        FilterChip(
                            selected = range == item,
                            onClick = { range = item },
                            label = { Text(item.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("脱敏导出", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "隐藏发送者、手机号和银行卡号，保留验证码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = masked, onCheckedChange = { masked = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(format, range, masked) }) { Text("选择保存位置") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "密码用于跨设备恢复；同时会绑定本机密钥，本机可用指纹快速恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("备份密码（至少 8 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    password.length < 8 -> "密码至少 8 位"
                    password != confirm -> "两次输入的密码不一致"
                    else -> {
                        onConfirm(password)
                        null
                    }
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BackupImportDialog(
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onPassword: (String) -> Unit,
    onBiometric: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复配置备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "导入会替换推送、规则、外观等设置；应用锁与历史记录保持不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (biometricAvailable) {
                    OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                        Text("使用本机指纹 / 面容恢复")
                    }
                    Text("或使用备份密码：", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("备份密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (password.isNotEmpty()) onPassword(password)
            }) { Text("用密码恢复") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun launchSaf(context: Context, launch: () -> Unit) {
    com.dodocomm.app.core.system.ExternalNavigation.suppressNextLeave()
    runCatching(launch).onFailure { error ->
        Toast.makeText(
            context,
            "无法打开系统文件选择器：${error.message ?: error.javaClass.simpleName}",
            Toast.LENGTH_LONG,
        ).show()
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
        var languageExpanded by remember { mutableStateOf(false) }
        Box {
            SettingActionRow(
                title = "语言",
                subtitle = "界面文字语言（应用内）",
                value = if (settings.appLanguage == AppLanguage.SYSTEM) {
                    com.dodocomm.app.ui.i18n.AppStrings.translate(settings.appLanguage.label)
                } else {
                    settings.appLanguage.label
                },
                translateValue = settings.appLanguage == AppLanguage.SYSTEM,
                onClick = { languageExpanded = true },
            )
            DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                AppLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            if (language == AppLanguage.SYSTEM) {
                                Text(language.label)
                            } else {
                                MaterialText(language.label)
                            }
                        },
                        onClick = {
                            viewModel.setAppLanguage(language)
                            languageExpanded = false
                        },
                        trailingIcon = {
                            if (settings.appLanguage == language) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }
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
                is UpdateState.Ready -> "可安装"
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
