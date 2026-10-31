@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.channel.ChannelSendResult
import com.dodocomm.app.core.channel.ChannelType
import com.dodocomm.app.core.channel.PushChannel
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.settings.CopyMode
import com.dodocomm.app.core.settings.PushLevel
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.CopyModeRow
import com.dodocomm.app.ui.components.InlineResult
import com.dodocomm.app.ui.components.SecretField
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.SettingTextField
import com.dodocomm.app.ui.components.Text
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val editing = editingId?.let { id -> settings.channels.firstOrNull { it.id == id } }

    if (editing != null) {
        ChannelEditContent(
            channel = editing,
            onBack = { editingId = null },
            onChange = viewModel::updateChannel,
            onDelete = {
                viewModel.removeChannel(editing.id)
                editingId = null
            },
            onTest = { viewModel.testChannel(editing.id) },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "推送通道",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "配置好的通道会一起收到短信",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 36.dp,
            ),
        ) {
            item(key = "intro") {
                Text(
                    text = "每个启用并填写完整的通道都会收到短信，至少保留一个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            item(key = "channels") {
                SectionCard {
                    settings.channels.forEachIndexed { index, channel ->
                        if (index > 0) com.dodocomm.app.ui.components.RowDivider()
                        ChannelRow(
                            channel = channel,
                            configured = channel.isConfigured(),
                            onOpen = { editingId = channel.id },
                            onToggle = { enabled -> viewModel.setChannelEnabled(channel.id, enabled) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "add") {
                Button(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加推送通道")
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text("添加推送通道", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                ChannelType.entries.filter { it != ChannelType.BARK }.forEach { type ->
                    SettingActionRow(
                        title = type.label,
                        subtitle = channelTypeDescription(type),
                        value = if (settings.channels.any { it.type == type }) "已添加" else null,
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = {
                            val channel = viewModel.addChannel(type)
                            showAddSheet = false
                            editingId = channel.id
                        },
                    )
                }
                if (settings.channels.none { it.type == ChannelType.BARK }) {
                    SettingActionRow(
                        title = "Bark",
                        subtitle = channelTypeDescription(ChannelType.BARK),
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = {
                            viewModel.addChannel(ChannelType.BARK)
                            showAddSheet = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: PushChannel,
    configured: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${channel.type.label} · ${channelStatus(channel, configured)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = channel.enabled, onCheckedChange = onToggle)
    }
}

private fun channelStatus(channel: PushChannel, configured: Boolean): String = when {
    !channel.enabled -> "已停用"
    !configured -> "未配置"
    else -> "已启用"
}

private fun channelTypeDescription(type: ChannelType): String = when (type) {
    ChannelType.BARK -> "Bark 推送"
    ChannelType.NTFY -> "轻量推送，支持自建服务器"
    ChannelType.TELEGRAM -> "通过 Telegram 机器人接收"
    ChannelType.PUSHOVER -> "适合手机、平板等多设备"
    ChannelType.EMAIL -> "把短信发到你的邮箱"
}

@Composable
private fun ChannelEditContent(
    channel: PushChannel,
    onBack: () -> Unit,
    onChange: (PushChannel) -> Unit,
    onDelete: () -> Unit,
    onTest: suspend () -> ChannelSendResult,
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ChannelSendResult?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    suspend fun runTest() {
        testing = true
        testResult = runCatching { onTest() }.getOrElse { error ->
            ChannelSendResult(channel.id, channel.name, channel.type, false, error.message ?: "测试失败")
        }
        testing = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleLarge)
                Text(channel.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (channel.type != ChannelType.BARK) {
                TextButton(onClick = { showDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item(key = "enabled") {
                SectionCard {
                    SettingSwitchRow(
                        title = "启用此通道",
                        checked = channel.enabled,
                        onCheckedChange = { onChange(channel.copy(enabled = it)) },
                        subtitle = "关闭后不会向此通道发送短信",
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingTextField(
                        value = channel.name,
                        onCommit = { onChange(channel.copy(name = it.ifBlank { channel.type.label })) },
                        label = "通道名称",
                        placeholder = channel.type.label,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "fields") {
                SectionCard {
                    when (channel.type) {
                        ChannelType.BARK -> BarkFields(channel, onChange)
                        ChannelType.NTFY -> NtfyFields(channel, onChange)
                        ChannelType.TELEGRAM -> TelegramFields(channel, onChange)
                        ChannelType.PUSHOVER -> PushoverFields(channel, onChange)
                        ChannelType.EMAIL -> EmailFields(channel, onChange)
                    }
                }
            }
            item(key = "test") {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { scope.launch { runTest() } },
                    enabled = !testing,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在测试…")
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("测试此通道")
                    }
                }
                testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    InlineResult(text = result.message, success = result.success)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这个通道？") },
            text = { Text("删除后需要重新填写配置。") },
            confirmButton = {
                TextButton(
                    onClick = { showDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun BarkFields(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    SettingTextField(
        value = channel.serverUrl,
        onCommit = { onChange(channel.copy(serverUrl = it.trim())) },
        label = "服务器地址",
        placeholder = "https://api.day.app",
    )
    Spacer(Modifier.height(12.dp))
    SecretField(
        value = channel.deviceKey,
        onCommit = { onChange(channel.copy(deviceKey = it.trim())) },
        label = "Device Key",
        placeholder = "在 Bark App 的服务器页面查看",
    )
    Spacer(Modifier.height(16.dp))
    Text("通知样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    SettingTextField(
        value = channel.pushGroup,
        onCommit = { onChange(channel.copy(pushGroup = it.trim())) },
        label = "通知分组",
        placeholder = "短信",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.pushSound,
        onCommit = { onChange(channel.copy(pushSound = it.trim())) },
        label = "通知声音",
        placeholder = "默认",
    )
    Spacer(Modifier.height(12.dp))
    BarkLevelPicker(channel, onChange)
    Spacer(Modifier.height(8.dp))
    CopyModeRow(
        selected = CopyMode.fromName(channel.copyMode),
         onSelect = { onChange(channel.copy(copyMode = it.name)) },
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.pushIcon,
        onCommit = { onChange(channel.copy(pushIcon = it.trim())) },
        label = "图标 URL",
        placeholder = "https://…/icon.png",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.pushUrl,
        onCommit = { onChange(channel.copy(pushUrl = it.trim())) },
        label = "点击跳转 URL",
        placeholder = "https://…（可选）",
    )
    Spacer(Modifier.height(8.dp))
    com.dodocomm.app.ui.components.RowDivider()
    SettingSwitchRow(
        title = "归档推送",
        checked = channel.isArchive,
        onCheckedChange = { onChange(channel.copy(isArchive = it)) },
        subtitle = "在 Bark 历史中保留这条推送",
    )
    com.dodocomm.app.ui.components.RowDivider()
    SettingSwitchRow(
        title = "显示角标",
        checked = channel.badgeEnabled,
        onCheckedChange = { onChange(channel.copy(badgeEnabled = it)) },
        subtitle = "Bark App 图标显示未读数",
    )
}

@Composable
private fun BarkLevelPicker(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = PushLevel.fromName(channel.pushLevel)
    androidx.compose.foundation.layout.Box {
        SettingActionRow(
            title = "推送级别",
            subtitle = "决定接收端如何提醒这条推送",
            value = current.label,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PushLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        onChange(channel.copy(pushLevel = level.apiValue))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NtfyFields(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    SettingTextField(
        value = channel.serverUrl,
        onCommit = { onChange(channel.copy(serverUrl = it.trim())) },
        label = "服务器地址",
        placeholder = "https://ntfy.sh",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.topic,
        onCommit = { onChange(channel.copy(topic = it.trim())) },
        label = "Topic",
        placeholder = "例如 dodo-sms",
    )
    Spacer(Modifier.height(12.dp))
    SecretField(
        value = channel.token,
        onCommit = { onChange(channel.copy(token = it.trim())) },
        label = "访问令牌（可选）",
        placeholder = "ntfy access token",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.priority,
        onCommit = { onChange(channel.copy(priority = it.trim())) },
        label = "优先级 1–5（可选）",
        placeholder = "3",
    )
}

@Composable
private fun TelegramFields(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    SecretField(
        value = channel.botToken,
        onCommit = { onChange(channel.copy(botToken = it.trim())) },
        label = "机器人 Token",
        placeholder = "123456:ABC…",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.chatId,
        onCommit = { onChange(channel.copy(chatId = it.trim())) },
        label = "会话 ID",
        placeholder = "例如 123456789",
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "向 @BotFather 创建 Bot，把 Bot 拉进目标聊天后获取 Chat ID。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PushoverFields(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    SecretField(
        value = channel.userKey,
        onCommit = { onChange(channel.copy(userKey = it.trim())) },
        label = "用户 Key",
        placeholder = "Pushover user key",
    )
    Spacer(Modifier.height(12.dp))
    SecretField(
        value = channel.appToken,
        onCommit = { onChange(channel.copy(appToken = it.trim())) },
        label = "应用 Token",
        placeholder = "Pushover application token",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.priority,
        onCommit = { onChange(channel.copy(priority = it.trim())) },
        label = "优先级 -2–2（可选）",
        placeholder = "0",
    )
}

@Composable
private fun EmailFields(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    SettingTextField(
        value = channel.smtpHost,
        onCommit = { onChange(channel.copy(smtpHost = it.trim())) },
        label = "邮箱服务器",
        placeholder = "smtp.example.com",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.smtpPort.toString(),
        onCommit = { onChange(channel.copy(smtpPort = it.trim().toIntOrNull() ?: 587)) },
        label = "端口",
        placeholder = "587",
    )
    Spacer(Modifier.height(12.dp))
    EmailSecurityPicker(channel, onChange)
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.smtpUsername,
        onCommit = { onChange(channel.copy(smtpUsername = it.trim())) },
        label = "用户名 / 邮箱",
        placeholder = "you@example.com",
    )
    Spacer(Modifier.height(12.dp))
    SecretField(
        value = channel.smtpPassword,
        onCommit = { onChange(channel.copy(smtpPassword = it)) },
        label = "授权码（不是邮箱密码）",
        placeholder = "邮箱授权码",
    )
    Spacer(Modifier.height(12.dp))
    SettingTextField(
        value = channel.recipient,
        onCommit = { onChange(channel.copy(recipient = it.trim())) },
        label = "收件人",
        placeholder = "to@example.com",
    )
}

@Composable
private fun EmailSecurityPicker(channel: PushChannel, onChange: (PushChannel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("STARTTLS", "SSL", "NONE")
    androidx.compose.foundation.layout.Box {
        SettingActionRow(
            title = "加密方式",
            value = channel.smtpSecurity,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(channel.copy(smtpSecurity = option))
                        expanded = false
                    },
                )
            }
        }
    }
}
