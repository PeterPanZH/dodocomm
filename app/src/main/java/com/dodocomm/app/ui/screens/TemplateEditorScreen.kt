@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.model.SmsPayload
import com.dodocomm.app.core.settings.AppSettings
import com.dodocomm.app.core.system.DeviceName
import com.dodocomm.app.forward.TitleTemplate
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SettingTextField
import com.dodocomm.app.ui.components.Text
import com.dodocomm.app.ui.model.TestPushState
import com.dodocomm.app.ui.theme.FieldShape

private data class TemplateVariable(
    val token: String,
    val name: String,
    val example: String,
    val bodyOnly: Boolean = false,
)

private val templateVariables = listOf(
    TemplateVariable("{device}", "设备名称", "Pixel 9"),
    TemplateVariable("{sender}", "发件号码", "95588"),
    TemplateVariable("{body}", "短信原文", "您的验证码是 123456", bodyOnly = true),
    TemplateVariable("{sim}", "SIM 卡 / 运营商", "SIM 1 · 中国移动"),
    TemplateVariable("{number}", "接收号码", "13800138000（可能为空）"),
    TemplateVariable("{time}", "接收时间", "14:32"),
    TemplateVariable("{date}", "接收日期", "2026-10-31"),
    TemplateVariable("{datetime}", "日期和时间", "2026-10-31 14:32"),
)

@Composable
fun TemplateEditorScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testState by viewModel.templateTestState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemDeviceName = rememberSaveable { DeviceName.resolve(context) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    var titleValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                settings.titleTemplate,
                selection = TextRange(settings.titleTemplate.length),
            ),
        )
    }
    var bodyValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                settings.bodyTemplate,
                selection = TextRange(settings.bodyTemplate.length),
            ),
        )
    }

    LaunchedEffect(settings.titleTemplate) {
        if (titleValue.text != settings.titleTemplate) {
            titleValue = TextFieldValue(settings.titleTemplate)
        }
    }
    LaunchedEffect(settings.bodyTemplate) {
        if (bodyValue.text != settings.bodyTemplate) {
            bodyValue = TextFieldValue(settings.bodyTemplate)
        }
    }

    val activeValue = if (tab == 0) titleValue else bodyValue
    val activeTokens = templateVariables.filter { tab != 0 || !it.bodyOnly }

    fun insert(token: String) {
        val current = if (tab == 0) titleValue else bodyValue
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val newText = current.text.replaceRange(start, end, token)
        val newValue = TextFieldValue(newText, selection = TextRange(start + token.length))
        if (tab == 0) {
            titleValue = newValue
            viewModel.setTitleTemplate(newText)
        } else {
            bodyValue = newValue
            viewModel.setBodyTemplate(newText)
        }
    }

    fun onActiveValueChange(value: TextFieldValue) {
        if (tab == 0) {
            titleValue = value
            viewModel.setTitleTemplate(value.text)
        } else {
            bodyValue = value
            viewModel.setBodyTemplate(value.text)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
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
                    text = "模板编辑器",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "编辑标题、正文和来源变量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showResetDialog = true }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("恢复默认")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            SectionCard {
                SettingTextField(
                    value = settings.deviceName,
                    onCommit = viewModel::setDeviceName,
                    label = "设备名称",
                    placeholder = systemDeviceName,
                    supportingText = "用于 {device} 变量；留空时读取系统里的手机名称",
                )
            }

            Spacer(Modifier.height(18.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("标题模板", "消息体模板").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = activeValue,
                onValueChange = ::onActiveValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text(if (tab == 0) "标题模板" else "消息体模板") },
                placeholder = {
                    Text(
                        if (tab == 0) {
                            "{device} · {sender}"
                        } else {
                            "{body}\n\n{datetime} · {sim} · {number}"
                        },
                    )
                },
                supportingText = {
                    Text(
                        if (tab == 0) {
                            "标题显示在 iPhone 通知的第一行"
                        } else {
                            "{body} 是短信原文；不含 {body} 时原文仍会保留"
                        },
                    )
                },
                singleLine = tab == 0,
                minLines = if (tab == 0) 1 else 5,
                maxLines = if (tab == 0) 1 else 10,
                shape = FieldShape,
            )

            Spacer(Modifier.height(16.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "插入变量",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showHelp = true }) {
                        Text("含义与示例")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点击变量，插入到当前模板的光标位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                activeTokens.chunked(2).forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { variable ->
                            TemplateVariableCard(
                                variable = variable,
                                modifier = Modifier.weight(1f),
                                onClick = { insert(variable.token) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TemplatePreviewCard(
                titleTemplate = settings.titleTemplate,
                bodyTemplate = settings.bodyTemplate,
                deviceName = settings.deviceName.ifBlank { systemDeviceName },
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = viewModel::sendTemplateTestPush,
                    enabled = testState !is TestPushState.Loading,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (testState is TestPushState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在发送…")
                    } else {
                        Text("测试模板推送")
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "用当前模板发送一条示例短信",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (testState is TestPushState.Success || testState is TestPushState.Failure) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (val state = testState) {
                        is TestPushState.Success -> state.message
                        is TestPushState.Failure -> state.message
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testState is TestPushState.Success) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (showHelp) {
        TemplateVariableHelpSheet(onDismiss = { showHelp = false })
    }
    if (showResetDialog) {
        val isTitle = tab == 0
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认模板？") },
            text = { Text(if (isTitle) "标题会恢复为推荐模板。" else "消息体会恢复为推荐模板。") },
            confirmButton = {
                TextButton(onClick = {
                    if (isTitle) {
                        viewModel.setTitleTemplate(AppSettings.DEFAULT_TITLE_TEMPLATE)
                        titleValue = TextFieldValue(AppSettings.DEFAULT_TITLE_TEMPLATE)
                    } else {
                        viewModel.setBodyTemplate(AppSettings.DEFAULT_BODY_TEMPLATE)
                        bodyValue = TextFieldValue(AppSettings.DEFAULT_BODY_TEMPLATE)
                    }
                    showResetDialog = false
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TemplateVariableCard(
    variable: TemplateVariable,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = variable.token,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = variable.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = variable.example,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TemplatePreviewCard(
    titleTemplate: String,
    bodyTemplate: String,
    deviceName: String,
) {
    val now = System.currentTimeMillis()
    val sample = SmsPayload(
        sender = "95588",
        body = "【示例】您的验证码是 123456，5 分钟内有效。",
        smsTimestamp = now,
        receivedAt = now,
        simLabel = "SIM 1 · 示例运营商",
        deviceName = deviceName,
        receiverNumber = "13800138000",
    )
    val title = TitleTemplate.render(titleTemplate, sample)
    val body = TitleTemplate.renderBody(bodyTemplate, sample)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "实时预览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "示例数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "标题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "消息体",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateVariableHelpSheet(onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        ) {
            Text(
                text = "模板变量说明",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "变量会替换成当前短信的真实信息；取不到时自动省略。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            templateVariables.forEach { variable ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = variable.token,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = variable.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (variable.bodyOnly) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "仅消息体",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = "示例：${variable.example}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "{sim} 和 {number} 需要电话权限；取不到时会自动省略，不会影响短信转发。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
