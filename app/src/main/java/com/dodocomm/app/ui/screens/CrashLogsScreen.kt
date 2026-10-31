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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dodocomm.app.core.crash.CrashEntry
import com.dodocomm.app.core.crash.CrashLogger
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.InfoNote
import com.dodocomm.app.ui.components.SectionCard
import com.dodocomm.app.ui.components.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashLogsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(CrashLogger.entries(context)) }
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var showClear by remember { mutableStateOf(false) }

    fun copyAll() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("DodoComm crash logs", CrashLogger.toText(entries)))
        viewModel.postSnackbar("崩溃日志已复制")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("崩溃日志", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "只保存在本机，最多 10 条，不会自动上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isNotEmpty()) {
                IconButton(onClick = ::copyAll) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制全部")
                }
                TextButton(onClick = { showClear = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("清空")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    SectionCard {
                        InfoNote("还没有崩溃记录。如果 App 意外退出，这里会留下最近 10 次的本地日志。")
                    }
                }
            } else {
                entries.forEachIndexed { index, entry ->
                    item(key = "crash-$index") {
                        CrashCard(
                            entry = entry,
                            expanded = expandedIndex == index,
                            onClick = { expandedIndex = if (expandedIndex == index) -1 else index },
                        )
                    }
                }
            }
        }
    }

    if (showClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空崩溃日志？") },
            text = { Text("清空后无法恢复，但不会影响转发和设置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashLogger.clear(context)
                        entries = emptyList()
                        expandedIndex = -1
                        showClear = false
                        viewModel.postSnackbar("崩溃日志已清空")
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun CrashCard(entry: CrashEntry, expanded: Boolean, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    SectionCard {
        androidx.compose.material3.Surface(
            onClick = onClick,
            color = androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    text = formatter.format(Date(entry.time)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "v${entry.versionName} · ${entry.thread}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expanded) {
                    Spacer(Modifier.padding(top = 8.dp))
                    SelectionContainer {
                        Text(
                            text = entry.stackTrace,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                } else {
                    Text(
                        text = entry.stackTrace.lineSequence().take(2).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
