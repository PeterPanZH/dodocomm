@file:OptIn(ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sms
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.remote.RemoteAction
import com.dodocomm.app.core.remote.RemoteCommand
import com.dodocomm.app.core.remote.RemoteCommandParser
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.RowDivider
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.SectionHeader
import com.dodocomm.app.ui.components.SettingSwitchRow
import com.dodocomm.app.ui.components.SettingTextField
import com.dodocomm.app.ui.components.Text

@Composable
fun RemoteManagementScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var secretVisible by rememberSaveable { mutableStateOf(false) }
    var generatedCommand by rememberSaveable { mutableStateOf("") }
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }

    fun copy(text: String, label: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModel.postSnackbar("已复制")
    }

    fun generateSample() {
        val nonce = System.currentTimeMillis() / 1000
        val command = RemoteCommand(RemoteAction.STATUS, 0, nonce, "")
        val signature = RemoteCommandParser.sign(settings.remoteSecret, command)
        generatedCommand = "#DODO STATUS $nonce $signature"
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
                Text("远程管理", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "用可信号码发签名短信来控制转发",
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
            item(key = "enable") {
                SectionCard {
                    SettingSwitchRow(
                        title = "允许签名短信远程管理",
                        checked = settings.remoteEnabled,
                        onCheckedChange = viewModel::setRemoteEnabled,
                        subtitle = "默认关闭。开启后只接受可信号码发来的签名指令。",
                        icon = Icons.Rounded.Sms,
                    )
                    RowDivider()
                    SettingTextField(
                        value = settings.remoteTrustedNumber,
                        onCommit = viewModel::setRemoteTrustedNumber,
                        label = "可信号码",
                        placeholder = "+86 138…（必须完全一致）",
                    )
                }
            }

            if (settings.remoteEnabled) {
                item(key = "secret") {
                    Column {
                        SectionHeader("签名密钥")
                        SectionCard {
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
                                    Text("远程管理密钥", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (secretVisible) settings.remoteSecret else "••••••••••••••••••••",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { secretVisible = !secretVisible }) {
                                    Text(if (secretVisible) "隐藏" else "显示")
                                }
                                IconButton(onClick = { copy(settings.remoteSecret, "远程管理密钥") }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制密钥")
                                }
                                IconButton(onClick = viewModel::regenerateRemoteSecret) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "更换密钥")
                                }
                            }
                            RowDivider()
                            InfoNote("密钥不会自动上传；如果导出配置备份，会包含在加密备份里。更换密钥后旧指令立即失效。")
                        }
                    }
                }

                item(key = "format") {
                    Column {
                        SectionHeader("指令格式")
                        SectionCard {
                            SelectionContainer {
                                Text(
                                    text = """
                                        #DODO STATUS <nonce> <hmac>
                                        #DODO PAUSE 30 <nonce> <hmac>
                                        #DODO RESUME <nonce> <hmac>
                                    """.trimIndent(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            InfoNote("nonce 是当前 Unix 秒级时间戳，必须在 5 分钟内发送；hmac 是 HMAC-SHA256(\"ACTION|minutes|nonce\") 的小写十六进制。")
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = ::generateSample,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("生成一条示例 STATUS 指令")
                            }
                            if (generatedCommand.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(
                                        text = generatedCommand,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                TextButton(onClick = { copy(generatedCommand, "远程指令") }) {
                                    Text("复制示例指令")
                                }
                            }
                        }
                    }
                }

                item(key = "security") {
                    Column {
                        SectionHeader("安全说明")
                        SectionCard {
                            InfoNote("只接受这个可信号码发来的 #DODO 指令，其他号码和普通短信照常处理。")
                            Spacer(Modifier.height(8.dp))
                            InfoNote("签名校验失败或时间戳过期时不会执行，也不会回复，避免短信轰炸。")
                            Spacer(Modifier.height(8.dp))
                            InfoNote("远程指令只支持状态查询、暂停和恢复，不能删除数据、查看短信或修改配置。")
                        }
                    }
                }
            }
        }
    }
}
