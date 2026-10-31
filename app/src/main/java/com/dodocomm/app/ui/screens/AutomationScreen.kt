@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import com.dodocomm.app.ui.i18n.AppStrings
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.widget.DodoWidgetProvider
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.SettingActionRow
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.Text

@Composable
fun AutomationScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var confirmRegenerate by rememberSaveable { mutableStateOf(false) }
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val canPinWidget = remember(appWidgetManager) {
        appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun copyToken() {
        clipboard?.setPrimaryClip(ClipData.newPlainText("DodoComm automation token", settings.automationToken))
        viewModel.postSnackbar("令牌已复制")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = AppStrings.translate("返回"))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("自动化与组件", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "桌面小组件、快捷方式与自动化接口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "widget") {
                Column {
                    SectionHeader("桌面小组件")
                    SectionCard {
                        SettingActionRow(
                            title = "添加桌面小组件",
                            subtitle = "长按桌面 → 小组件 → 嘟嘟通讯，可选 2×2 / 4×2 / 4×1 / 2×3",
                            icon = Icons.Rounded.AddToHomeScreen,
                            onClick = {
                                if (canPinWidget) {
                                    runCatching {
                                        appWidgetManager.requestPinAppWidget(
                                            ComponentName(context, DodoWidgetProvider::class.java),
                                            null,
                                            null,
                                        )
                                    }.onFailure {
                                        viewModel.postSnackbar("当前桌面不支持直接添加，请长按桌面手动添加", isError = true)
                                    }
                                } else {
                                    viewModel.postSnackbar("请长按桌面 → 小组件，手动添加嘟嘟通讯")
                                }
                            },
                        )
                        RowDivider()
                        InfoNote("小组件会按当前桌面尺寸自动切换布局，显示今日送达数和转发状态，可直接暂停 30 分钟或打开 App。")
                    }
                }
            }

            item(key = "shortcuts") {
                Column {
                    SectionHeader("应用快捷方式")
                    SectionCard {
                        InfoNote("长按桌面上的嘟嘟通讯图标，可以快速「测试推送」或「暂停/恢复转发」，不用先打开 App。")
                    }
                }
            }

            item(key = "automation") {
                Column {
                    SectionHeader("自动化接口")
                    SectionCard {
                        SettingSwitchRow(
                            title = "允许自动化接口",
                            checked = settings.automationEnabled,
                            onCheckedChange = viewModel::setAutomationEnabled,
                            subtitle = "默认关闭。开启后，Tasker / adb 等工具可以用令牌控制非破坏性动作。",
                            icon = Icons.Rounded.SmartToy,
                        )
                        if (settings.automationEnabled) {
                            RowDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("访问令牌", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (tokenVisible) settings.automationToken else "••••••••••••••••",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Text(if (tokenVisible) "隐藏" else "显示")
                                }
                                IconButton(onClick = ::copyToken) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = AppStrings.translate("复制令牌"))
                                }
                                IconButton(onClick = { confirmRegenerate = true }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = AppStrings.translate("更换令牌"))
                                }
                            }
                            RowDivider()
                            InfoNote("每次发送命令都必须带上这个令牌。更换令牌后，旧配置会立即失效。")
                        }
                    }
                }
            }

            if (settings.automationEnabled) {
                item(key = "commands") {
                    Column {
                        SectionHeader("可用命令")
                        SectionCard {
                            CommandRow("status", "查询转发状态、通道数量、今日送达和重试数")
                            RowDivider()
                            CommandRow("test_push", "向所有已配置的推送通道发一条测试消息")
                            RowDivider()
                            CommandRow("pause", "暂停转发，默认 30 分钟，可用 minutes 调整")
                            RowDivider()
                            CommandRow("resume", "立即恢复转发")
                            RowDivider()
                            CommandRow("check_update", "检查 GitHub Releases 是否有新版本")
                        }
                    }
                }
                item(key = "adb") {
                    Column {
                        SectionHeader("adb 示例")
                        SectionCard {
                            SelectionContainer {
                                Text(
                                    text = """
                                        adb shell am broadcast \
                                        -n com.dodocomm.app/.automation.AutomationReceiver \
                                        -a com.dodocomm.app.action.AUTOMATION \
                                        --es token ${settings.automationToken} \
                                        --es command status
                                    """.trimIndent(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            InfoNote("把 command 换成 test_push / pause / resume / check_update 即可。pause 可加 --ei minutes 60。")
                        }
                    }
                }
                item(key = "tasker") {
                    Column {
                        SectionHeader("Tasker 配置")
                        SectionCard {
                            InfoNote("新建 Action → Send Intent，Action 填 com.dodocomm.app.action.AUTOMATION，Extra 增加 token 和 command；Target 选 Broadcast Receiver，Package 填 com.dodocomm.app，Class 填 com.dodocomm.app.automation.AutomationReceiver。")
                            Spacer(Modifier.height(8.dp))
                            InfoNote("接口只提供查询、测试、暂停/恢复和检查更新，不会读取短信内容，也不能删除数据。")
                        }
                    }
                }
            }

            item(key = "remote") {
                Column {
                    SectionHeader("远程管理")
                    SectionCard {
                        com.dodocomm.app.ui.components.SettingActionRow(
                            title = "签名短信远程管理",
                            subtitle = "用可信号码发签名短信暂停、恢复或查询状态",
                            value = if (settings.remoteEnabled) "已开启" else "未开启",
                            onClick = onOpenRemote,
                        )
                    }
                }
            }

            item(key = "security") {
                Column {
                    SectionHeader("安全说明")
                    SectionCard {
                        InfoNote("自动化接口默认关闭，并需要每次携带令牌。令牌等同于操作权限，请不要发给他人。")
                        Spacer(Modifier.height(8.dp))
                        InfoNote("如果你不使用 Tasker 或 adb，建议保持关闭。")
                    }
                }
            }
        }
    }

    if (confirmRegenerate) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("更换自动化令牌？") },
            text = { Text("旧令牌会立即失效，所有 Tasker / adb 配置都需要更新。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    viewModel.regenerateAutomationToken()
                }) { Text("更换") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CommandRow(command: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(command, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
