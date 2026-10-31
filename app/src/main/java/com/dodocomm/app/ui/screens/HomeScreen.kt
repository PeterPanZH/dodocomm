@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Sms
import com.dodocomm.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.dodocomm.app.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.TodayStats
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.system.AppPermissions
import com.dodocomm.app.core.system.ExternalNavigation
import com.dodocomm.app.core.system.SystemNavigator
import com.dodocomm.app.core.util.TimeFormatter
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.GradientSwitch
import com.dodocomm.app.ui.components.IconBubble
import com.dodocomm.app.ui.components.RecordCard
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.ScreenTitle
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.StatPill
import com.dodocomm.app.ui.components.greeting
import com.dodocomm.app.ui.components.todayLabel
import com.dodocomm.app.ui.model.HistoryFilter
import com.dodocomm.app.ui.model.PermissionState
import com.dodocomm.app.ui.model.SetupItem
import com.dodocomm.app.ui.model.TestPushState
import com.dodocomm.app.ui.theme.CardShape
import com.dodocomm.app.ui.theme.FilteredDark
import com.dodocomm.app.ui.theme.FilteredLight
import com.dodocomm.app.ui.theme.HeroShape
import com.dodocomm.app.ui.theme.LocalBrandGradient
import com.dodocomm.app.ui.theme.LocalIsDarkTheme
import com.dodocomm.app.ui.theme.PendingDark
import com.dodocomm.app.ui.theme.PendingLight
import com.dodocomm.app.ui.theme.SentDark
import com.dodocomm.app.ui.theme.SentLight
import com.dodocomm.app.ui.theme.TonalPair
import com.dodocomm.app.ui.theme.brandBrush

private object SetupIds {
    const val SMS = "sms"
    const val NOTIFICATIONS = "notifications"
    const val SERVER = "server"
    const val BATTERY = "battery"
    const val AUTOSTART = "autostart"
}

@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateHistory: (HistoryFilter?) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateGuide: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val testPushState by viewModel.testPushState.collectAsStateWithLifecycle()
    val dark = LocalIsDarkTheme.current

    val context = LocalContext.current
    val corePermissions = remember { AppPermissions.coreRuntimePermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshSystemState() }

    val setupItems = remember(settings, permissions) { buildSetupItems(settings, permissions) }
    val setupDone = setupItems.count { it.done }
    val setupTotal = setupItems.size
    val lastRecord = records.firstOrNull()

    fun launchPermissions() {
        ExternalNavigation.suppressNextLeave()
        permissionLauncher.launch(corePermissions)
    }

    fun handleSetupAction(item: SetupItem) {
        when (item.id) {
            SetupIds.SMS -> when {
                !permissions.receiveSms || !permissions.postNotifications -> launchPermissions()
                !permissions.notificationsEnabled -> SystemNavigator.openNotificationSettings(context)
                else -> SystemNavigator.openAppDetails(context)
            }

            SetupIds.NOTIFICATIONS -> if (!permissions.notificationsReady) {
                launchPermissions()
            } else {
                SystemNavigator.openNotificationSettings(context)
            }

            SetupIds.SERVER -> onNavigateSettings()
            SetupIds.BATTERY -> if (permissions.batteryOptimizationsIgnored) {
                SystemNavigator.openBatteryOptimizationSettings(context)
            } else {
                SystemNavigator.requestIgnoreBatteryOptimizations(context)
            }
            SetupIds.AUTOSTART -> SystemNavigator.openAutoStartSettings(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        ScreenTitle(title = greeting(), subtitle = todayLabel())
        Spacer(Modifier.height(18.dp))

        HomeHeroCard(
            configured = settings.hasAnyConfiguredChannel,
            forwardingEnabled = settings.forwardingEnabled,
            todaySent = stats.sent,
            onToggle = viewModel::setForwardingEnabled,
            onGuideClick = onNavigateGuide,
            onOpenSettings = onNavigateSettings,
        )

        Spacer(Modifier.height(16.dp))
        TodayStatsRow(
            stats = stats,
            dark = dark,
            onSentClick = { onNavigateHistory(HistoryFilter.SENT) },
            onRetryClick = { onNavigateHistory(HistoryFilter.RETRY) },
            onFilteredClick = { onNavigateHistory(HistoryFilter.FILTERED) },
        )

        if (!settings.hasAnyConfiguredChannel) {
            Spacer(Modifier.height(18.dp))
            ServerPromptCard(onClick = onNavigateSettings)
        }

        if (setupDone < setupTotal) {
            Spacer(Modifier.height(18.dp))
            SetupChecklistCard(
                items = setupItems,
                done = setupDone,
                total = setupTotal,
                onAction = ::handleSetupAction,
            )
        }

        if (lastRecord != null) {
            Spacer(Modifier.height(22.dp))
            RecentSection(
                record = lastRecord,
                onOpen = {
                    viewModel.requestOpenRecord(lastRecord.id)
                    onNavigateHistory(null)
                },
                onSeeAll = { onNavigateHistory(null) },
            )
        } else if (settings.hasAnyConfiguredChannel) {
            Spacer(Modifier.height(18.dp))
            WaitingCard()
        }

        Spacer(Modifier.height(22.dp))
        QuickActionsSection(
            testPushState = testPushState,
            onTestPush = viewModel::sendTestPush,
            onGuide = onNavigateGuide,
        )

        Spacer(Modifier.height(24.dp))
        PrivacyFooter()
        Spacer(Modifier.height(44.dp))
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeroCard(
    configured: Boolean,
    forwardingEnabled: Boolean,
    todaySent: Int,
    onToggle: (Boolean) -> Unit,
    onGuideClick: () -> Unit,
    onOpenSettings: () -> Unit,
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
                        color = Color.White.copy(alpha = 0.15f),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * 0.98f, -size.height * 0.12f),
                    )
                    drawCircle(
                        color = Color(0xFFF7CA7B).copy(alpha = 0.22f),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.02f, size.height * 1.1f),
                    )
                }
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.dodoco_mascot),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "嘟嘟通讯",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "嘟嘟可传信 · 短信即时送达",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.74f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClickLabel = "打开保活向导", onClick = onGuideClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "保活向导",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                val title = when {
                    !forwardingEnabled -> "已暂停"
                    !configured -> "待配置"
                    else -> "正在转发"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = title,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                            label = "forwardingState",
                        ) { animatedTitle ->
                            Text(
                                text = animatedTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                !configured -> "先配置推送通道，短信才能送达"
                                !forwardingEnabled -> "点击开启转发，短信会自动推送"
                                todaySent > 0 -> "今日已送达 $todaySent 条"
                                else -> "已开启转发，新短信会自动推送"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    GradientSwitch(
                        checked = forwardingEnabled,
                        onCheckedChange = onToggle,
                    )
                }

                val hintText: String? = if (!configured) "去配置 Bark 服务器" else null
                if (hintText != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Today stats
// ---------------------------------------------------------------------------

@Composable
private fun TodayStatsRow(
    stats: TodayStats,
    dark: Boolean,
    onSentClick: () -> Unit,
    onRetryClick: () -> Unit,
    onFilteredClick: () -> Unit,
) {
    val sentTonal = if (dark) SentDark else SentLight
    val pendingTonal = if (dark) PendingDark else PendingLight
    val filteredTonal = if (dark) FilteredDark else FilteredLight
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(
            value = stats.sent,
            label = "已送达",
            tonal = sentTonal,
            onClick = onSentClick,
            modifier = Modifier.weight(1f),
        )
        StatPill(
            value = stats.failed + stats.pending,
            label = "待重试",
            tonal = pendingTonal,
            onClick = onRetryClick,
            modifier = Modifier.weight(1f),
        )
        StatPill(
            value = stats.filtered,
            label = "已过滤",
            tonal = filteredTonal,
            onClick = onFilteredClick,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------
// Setup checklist
// ---------------------------------------------------------------------------

@Composable
private fun SetupChecklistCard(
    items: List<SetupItem>,
    done: Int,
    total: Int,
    onAction: (SetupItem) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon = Icons.Rounded.Autorenew, size = 36, iconSize = 18)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "还差 ${total - done} 步完成设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "完成后转发会更稳定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$done/$total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            if (index > 0) RowDivider()
            SetupRow(item = item, onClick = { onAction(item) })
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = item.actionLabel, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(
            icon = setupIcon(item.id),
            size = 34,
            iconSize = 18,
            containerColor = if (item.done) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (item.done) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (item.done) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = item.actionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
    }
}

private fun setupIcon(id: String): ImageVector = when (id) {
    SetupIds.SMS -> Icons.Rounded.Sms
    SetupIds.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
    SetupIds.SERVER -> Icons.Rounded.Dns
    SetupIds.BATTERY -> Icons.Rounded.BatterySaver
    else -> Icons.Rounded.Autorenew
}

private fun buildSetupItems(settings: AppSettings, permissions: PermissionState): List<SetupItem> {
    val smsReady = permissions.receiveSms
    val notificationsReady = permissions.notificationsReady
    val serverReady = settings.hasAnyConfiguredChannel
    val batteryReady = permissions.batteryOptimizationsIgnored
    val autoStartReady = settings.autoStartOnBoot
    return listOf(
        SetupItem(
            id = SetupIds.SMS,
            title = "短信读取权限",
            subtitle = if (smsReady) "已授权，可以接收短信广播" else "未授权，无法接收短信",
            done = smsReady,
            actionLabel = if (smsReady) "已授权" else "去授权",
        ),
        SetupItem(
            id = SetupIds.NOTIFICATIONS,
            title = "通知权限",
            subtitle = if (notificationsReady) "已开启，可显示常驻通知" else "未开启，服务状态不可见",
            done = notificationsReady,
            actionLabel = if (notificationsReady) "已开启" else "去开启",
        ),
        SetupItem(
            id = SetupIds.SERVER,
            title = "推送通道",
            subtitle = if (serverReady) {
                "已配置 ${settings.activePushChannels().size} 个可用通道"
            } else {
                "还没有可用的推送通道"
            },
            done = serverReady,
            actionLabel = if (serverReady) "已配置" else "去配置",
        ),
        SetupItem(
            id = SetupIds.BATTERY,
            title = "电池优化豁免",
            subtitle = if (batteryReady) "系统不会冻结后台服务" else "可能被系统限制后台运行",
            done = batteryReady,
            actionLabel = if (batteryReady) "已豁免" else "去设置",
        ),
        SetupItem(
            id = SetupIds.AUTOSTART,
            title = "开机自动恢复",
            subtitle = if (autoStartReady) "重启后会尝试自动恢复转发" else "重启后需要手动打开应用",
            done = autoStartReady,
            actionLabel = if (autoStartReady) "已开启" else "去开启",
        ),
    )
}

// ---------------------------------------------------------------------------
// Unconfigured / waiting states
// ---------------------------------------------------------------------------

@Composable
private fun ServerPromptCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.dodoco_mascot),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "先配置推送通道",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "填入推送通道配置后，收到的短信就会自动推送。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
                Text("去配置")
            }
        }
    }
}

@Composable
private fun WaitingCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                icon = Icons.Rounded.MarkChatUnread,
                size = 44,
                iconSize = 22,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "等待第一条短信",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "收到短信后会自动推送，并在这里留下记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Recent + quick actions + footer
// ---------------------------------------------------------------------------

@Composable
private fun RecentSection(
    record: ForwardRecord,
    onOpen: () -> Unit,
    onSeeAll: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "最近转发",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        TextButton(
            onClick = onSeeAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("全部", style = MaterialTheme.typography.labelMedium)
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    RecordCard(
        record = record,
        timeLabel = TimeFormatter.formatRecordTime(record.receivedAt),
        onClick = onOpen,
    )
}

@Composable
private fun QuickActionsSection(
    testPushState: TestPushState,
    onTestPush: () -> Unit,
    onGuide: () -> Unit,
) {
    Text(
        text = "快捷操作",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickActionCard(
            title = "发送测试推送",
            subtitle = "验证服务器配置",
            icon = Icons.AutoMirrored.Rounded.Send,
            iconTonal = TonalPair(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            loading = testPushState is TestPushState.Loading,
            onClick = onTestPush,
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            title = "保活向导",
            subtitle = "提升后台稳定性",
            icon = Icons.Rounded.Bolt,
            iconTonal = TonalPair(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            loading = false,
            onClick = onGuide,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTonal: TonalPair,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = !loading,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTonal.container),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = iconTonal.content,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTonal.content,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyFooter() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "短信内容仅发送到你配置的推送通道或 SMTP 服务器",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}
