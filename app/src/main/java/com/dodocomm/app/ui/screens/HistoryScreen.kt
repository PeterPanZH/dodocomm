@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dodocomm.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import com.dodocomm.app.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dodocomm.app.core.model.BarkResult
import com.dodocomm.app.core.model.ForwardRecord
import com.dodocomm.app.core.model.ForwardStatus
import com.dodocomm.app.core.util.TimeFormatter
import com.dodocomm.app.ui.AppViewModel
import com.dodocomm.app.ui.components.ConfirmDialog
import com.dodocomm.app.ui.components.EmptyState
import com.dodocomm.app.ui.components.LabeledRow
import com.dodocomm.app.ui.components.RecordCard
import com.dodocomm.app.ui.components.SenderAvatar
import com.dodocomm.app.ui.components.StatusChip
import com.dodocomm.app.ui.components.displaySender
import com.dodocomm.app.ui.model.HistoryFilter
import com.dodocomm.app.ui.theme.InnerShape
import com.dodocomm.app.ui.theme.SheetShape
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val filter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRecordRequest.collectAsStateWithLifecycle()

    var selectedId by rememberSaveable { mutableStateOf(-1L) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(openRequest) {
        if (openRequest != -1L) {
            selectedId = openRequest
            viewModel.consumeOpenRecord()
        }
    }
    LaunchedEffect(records, selectedId) {
        if (selectedId != -1L && records.none { it.id == selectedId }) {
            selectedId = -1L
        }
    }

    val groups = remember(records, filter) { buildGroups(records, filter) }
    val counts = remember(records) {
        mapOf(
            HistoryFilter.ALL to records.size,
            HistoryFilter.SENT to records.count { it.status == ForwardStatus.SENT },
            HistoryFilter.RETRY to records.count {
                it.status == ForwardStatus.FAILED || it.status == ForwardStatus.PENDING
            },
            HistoryFilter.FILTERED to records.count { it.status == ForwardStatus.FILTERED },
        )
    }

    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val selectedRecord = remember(records, selectedId) { records.firstOrNull { it.id == selectedId } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "转发记录",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (records.isEmpty()) {
                        "短信送达状态都在这里"
                    } else {
                        "共 ${records.size} 条 · 本地保留最近 500 条"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("清空记录") },
                        onClick = {
                            menuExpanded = false
                            showClearDialog = true
                        },
                        enabled = records.isNotEmpty(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryFilter.entries.forEach { item ->
                val count = counts[item] ?: 0
                FilterChip(
                    selected = filter == item,
                    onClick = { viewModel.setHistoryFilter(item) },
                    label = {
                        Text(
                            text = if (count > 0) "${item.label} $count" else item.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Inbox,
                    mascotRes = R.drawable.dodoco_sleep,
                    title = "嘟嘟可还在等第一条短信",
                    message = "收到的短信会出现在这里，转发状态也能随时查看。",
                )
            }
        } else if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.FilterAlt,
                    mascotRes = R.drawable.dodoco_happy,
                    title = "嘟嘟可没找到符合条件的记录",
                    message = "换一个筛选条件再看看吧。",
                    actionLabel = "查看全部",
                    onAction = { viewModel.setHistoryFilter(HistoryFilter.ALL) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            ) {
                groups.forEach { group ->
                    stickyHeader(
                        key = "header_${group.header}",
                        contentType = "header",
                    ) {
                        DateHeader(header = group.header, count = group.records.size)
                    }
                    items(
                        items = group.records,
                        key = { record -> record.id },
                        contentType = { record -> record.status.name },
                    ) { record ->
                        Box(modifier = Modifier.padding(vertical = 5.dp)) {
                            RecordCard(
                                record = record,
                                timeLabel = TimeFormatter.formatClock(record.receivedAt),
                                onClick = { selectedId = record.id },
                            )
                        }
                    }
                }
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

    val record = selectedRecord
    if (record != null) {
        RecordDetailSheet(
            record = record,
            viewModel = viewModel,
            onDismiss = { selectedId = -1L },
            onCopy = { text ->
                clipboard?.setPrimaryClip(ClipData.newPlainText("短信内容", text))
                viewModel.postSnackbar("已复制短信内容")
            },
            onDelete = {
                viewModel.deleteRecord(record.id)
                selectedId = -1L
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Date grouping
// ---------------------------------------------------------------------------

private data class HistoryGroup(val header: String, val records: List<ForwardRecord>)

private fun buildGroups(records: List<ForwardRecord>, filter: HistoryFilter): List<HistoryGroup> {
    val now = System.currentTimeMillis()
    val grouped = LinkedHashMap<String, MutableList<ForwardRecord>>()
    records.forEach { record ->
        if (!filter.matches(record.status)) return@forEach
        val header = TimeFormatter.formatDateHeader(record.receivedAt, now)
        grouped.getOrPut(header) { mutableListOf() }.add(record)
    }
    return grouped.map { (header, list) -> HistoryGroup(header, list) }
}

@Composable
private fun DateHeader(header: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Detail sheet
// ---------------------------------------------------------------------------

@Composable
private fun RecordDetailSheet(
    record: ForwardRecord,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<BarkResult?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status = record.status)
                Spacer(Modifier.weight(1f))
                Text(
                    text = TimeFormatter.formatFull(record.receivedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(sender = record.sender, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displaySender(record.sender),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!record.simLabel.isNullOrBlank()) {
                        Text(
                            text = record.simLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                shape = InnerShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                SelectionContainer {
                    Text(
                        text = record.body.ifBlank { "（无正文）" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (!record.title.isNullOrBlank()) {
                LabeledRow(label = "推送标题", value = record.title)
            }
            LabeledRow(label = "收到时间", value = TimeFormatter.formatFull(record.receivedAt))
            if (record.attempts > 0) {
                LabeledRow(label = "尝试次数", value = "${record.attempts} 次")
            }
            if (!record.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InnerShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(14.dp),
                ) {
                    Text(
                        text = if (record.status == ForwardStatus.FILTERED) "过滤原因" else "失败原因",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = record.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            val result = lastResult
            if (result != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (result.success) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (result.success) {
                            "已重新发送，状态已更新"
                        } else {
                            "发送失败：${result.message.ifBlank { "未知原因" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onCopy(record.body) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复制")
                }
                Button(
                    onClick = {
                        if (!sending) {
                            scope.launch {
                                sending = true
                                lastResult = viewModel.resendRecord(record.id)
                                sending = false
                            }
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("重新发送")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("删除这条记录")
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除这条记录？",
            message = "删除后无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false },
        )
    }
}
