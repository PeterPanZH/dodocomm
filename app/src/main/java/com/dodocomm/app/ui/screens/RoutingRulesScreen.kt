@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dodocomm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.routing.RoutingRule
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.SettingTextField
import com.dodocomm.app.ui.components.Text

@Composable
fun RoutingRulesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> settings.routingRules.firstOrNull { it.id == id } }

    if (editing != null) {
        RuleEditor(
            settings = settings,
            rule = editing,
            onBack = { editingId = null },
            onChange = viewModel::updateRoutingRule,
            onDelete = {
                viewModel.removeRoutingRule(editing.id)
                editingId = null
            },
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
                Text("路由规则", style = MaterialTheme.typography.titleLarge)
                Text(
                    "按顺序匹配；首条命中后默认停止",
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
            item(key = "default") {
                SectionCard {
                    Text(
                        text = "默认目标",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "没有规则命中时，发送到所有已启用且配置完整的通道。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "rules") {
                if (settings.routingRules.isEmpty()) {
                    SectionCard {
                        Text(
                            text = "还没有规则，所有短信走默认目标。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SectionCard {
                        settings.routingRules.forEachIndexed { index, rule ->
                            if (index > 0) RowDivider()
                            RuleRow(
                                rule = rule,
                                settings = settings,
                                onOpen = { editingId = rule.id },
                                onToggle = { enabled -> viewModel.updateRoutingRule(rule.copy(enabled = enabled)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "add") {
                Button(
                    onClick = {
                        val rule = viewModel.addRoutingRule()
                        editingId = rule.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加规则")
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: RoutingRule,
    settings: AppSettings,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(rule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = ruleSummary(rule, settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun RuleEditor(
    settings: AppSettings,
    rule: RoutingRule,
    onBack: () -> Unit,
    onChange: (RoutingRule) -> Unit,
    onDelete: () -> Unit,
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val channelNames = settings.channels.associate { it.id to it.name }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Text(rule.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showDelete = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("删除")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item(key = "basic") {
                SectionCard {
                    SettingTextField(
                        value = rule.name,
                        onCommit = { onChange(rule.copy(name = it.ifBlank { "规则" })) },
                        label = "规则名称",
                        placeholder = "例如 银行短信走 ntfy",
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingSwitchRow(
                        title = "启用规则",
                        checked = rule.enabled,
                        onCheckedChange = { onChange(rule.copy(enabled = it)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "conditions") {
                SectionCard {
                    Text("条件（全部满足）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    SettingTextField(
                        value = rule.senderContains,
                        onCommit = { onChange(rule.copy(senderContains = it.trim())) },
                        label = "发送者包含（可选）",
                        placeholder = "95588",
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingTextField(
                        value = rule.keywordContains,
                        onCommit = { onChange(rule.copy(keywordContains = it.trim())) },
                        label = "内容关键词包含（可选）",
                        placeholder = "验证码",
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("SIM 槽位", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "任意", 1 to "SIM 1", 2 to "SIM 2").forEach { (slot, label) ->
                            FilterChip(
                                selected = rule.simSlot == slot,
                                onClick = { onChange(rule.copy(simSlot = slot)) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingTextField(
                            value = minutesToLabel(rule.timeStartMinutes),
                            onCommit = { onChange(rule.copy(timeStartMinutes = labelToMinutes(it))) },
                            label = "开始时间（可空）",
                            placeholder = "22:00",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.weight(1f),
                        )
                        SettingTextField(
                            value = minutesToLabel(rule.timeEndMinutes),
                            onCommit = { onChange(rule.copy(timeEndMinutes = labelToMinutes(it))) },
                            label = "结束时间（可空）",
                            placeholder = "07:00",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "targets") {
                SectionCard {
                    Text("目标通道", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "可多选；规则未选通道时回退默认目标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.channels.forEach { channel ->
                            val selected = channel.id in rule.targetChannelIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) {
                                        rule.targetChannelIds - channel.id
                                    } else {
                                        rule.targetChannelIds + channel.id
                                    }
                                    onChange(rule.copy(targetChannelIds = next))
                                },
                                label = { Text(channelNames[channel.id] ?: channel.name) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item(key = "behavior") {
                SectionCard {
                    SettingSwitchRow(
                        title = "命中后继续匹配下一条",
                        checked = rule.continueMatching,
                        onCheckedChange = { onChange(rule.copy(continueMatching = it)) },
                        subtitle = "关闭时首条命中即停止",
                    )
                    RowDivider()
                    SettingSwitchRow(
                        title = "目标无效时回退默认",
                        checked = rule.fallbackToDefault,
                        onCheckedChange = { onChange(rule.copy(fallbackToDefault = it)) },
                        subtitle = "目标通道被删除或停用时使用默认目标",
                    )
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这条规则？") },
            text = { Text("删除后无法恢复。") },
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

private fun ruleSummary(rule: RoutingRule, settings: AppSettings): String {
    val conditions = buildList {
        if (rule.senderContains.isNotBlank()) add("发送者含 ${rule.senderContains}")
        if (rule.keywordContains.isNotBlank()) add("内容含 ${rule.keywordContains}")
        if (rule.simSlot > 0) add("SIM ${rule.simSlot}")
        if (rule.timeStartMinutes >= 0 && rule.timeEndMinutes >= 0) {
            add("${minutesToLabel(rule.timeStartMinutes)}–${minutesToLabel(rule.timeEndMinutes)}")
        }
    }.ifEmpty { listOf("所有短信") }
    val targets = rule.targetChannelIds.mapNotNull { id ->
        settings.channels.firstOrNull { it.id == id }?.name
    }.ifEmpty { listOf("默认目标") }
    return conditions.joinToString(" · ") + " → " + targets.joinToString(" / ")
}

private fun minutesToLabel(minutes: Int): String =
    if (minutes < 0) "" else "%02d:%02d".format(minutes / 60, minutes % 60)

private fun labelToMinutes(label: String): Int {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(label.trim()) ?: return -1
    val hour = match.groupValues[1].toIntOrNull() ?: return -1
    val minute = match.groupValues[2].toIntOrNull() ?: return -1
    if (hour !in 0..23 || minute !in 0..59) return -1
    return hour * 60 + minute
}
