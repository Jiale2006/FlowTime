package com.example.flowtime.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import com.example.flowtime.data.StatsRules
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 记录页：跨任务的记录总览，倒序、按天分组。
 *
 * 筛选采用 **Gmail search chips 模式**（调研结论 2026-10-10：维度少但某维要搜索时，
 * 用「参数化 chip」而不是筛选弹层——M3 规范也认可 filter chip 的尾图标用于打开菜单）：
 * - 「任务：X ▾」点开底部面板（搜索 + 单选，子任务缩进）；按 **id** 精确筛，
 *   含它的直接子任务，与任务详情页口径一致
 * - 「时段：X ▾」点开下拉（全部/上午/下午/晚上/深夜），按记录**结束小时**判定
 * - 激活的非默认条件 chip 带 ×，点即清除；全部即时生效，无确认按钮（M3 chips 即时语义）
 */
@Composable
fun AllRecordsScreen(
    viewModel: AppViewModel,
    initialTaskId: String?,
    onBack: () -> Unit,
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var selectedTaskId by remember(initialTaskId) { mutableStateOf<String?>(initialTaskId) }
    var hourPreset by remember { mutableStateOf(StatsRules.HourPreset.ALL) }
    var taskPickerOpen by remember { mutableStateOf(false) }
    var hourMenuOpen by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    var deletedRecord by remember { mutableStateOf<FocusRecord?>(null) }

    val filtered = remember(records, tasks, selectedTaskId, hourPreset) {
        val task = tasks.firstOrNull { it.id == selectedTaskId }
        val belongs = task?.let { RecordLink.treePredicate(it, tasks) } ?: { _: FocusRecord -> true }
        records
            .filter { belongs(it) && StatsRules.inHourWindow(it, hourPreset.window, zone) }
            .sortedByDescending { it.endEpochMillis }
    }
    val selectedTask = remember(tasks, selectedTaskId) {
        selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
    }
    /** 活任务 id：记录挂着的任务不在这里面 = 任务已被删除（回收站里或已彻底删） */
    val liveTaskIds = remember(tasks) { tasks.mapTo(mutableSetOf()) { it.id } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text(
                    "全部记录",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 任务维度：选项多、要搜索 → 参数化 chip 打开选择面板
                ParamChip(
                    label = "任务：" + (selectedTask?.name ?: "全部"),
                    active = selectedTask != null,
                    onOpen = { taskPickerOpen = true },
                    onClear = { selectedTaskId = null },
                )
                // 时段维度：固定 5 档互斥 → 参数化 chip 打开下拉
                Box {
                    ParamChip(
                        label = "时段：" + hourPreset.label,
                        active = hourPreset != StatsRules.HourPreset.ALL,
                        onOpen = { hourMenuOpen = true },
                        onClear = { hourPreset = StatsRules.HourPreset.ALL },
                    )
                    DropdownMenu(
                        expanded = hourMenuOpen,
                        onDismissRequest = { hourMenuOpen = false },
                    ) {
                        StatsRules.HourPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                leadingIcon = {
                                    if (hourPreset == preset) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Primary)
                                    }
                                },
                                onClick = {
                                    hourPreset = preset
                                    hourMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "${filtered.size} 条",
                fontSize = 12.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(Dimens.CardPadding)) {
                        Text(
                            if (selectedTask != null || hourPreset != StatsRules.HourPreset.ALL) {
                                "当前筛选下没有记录，点 chip 上的 ✕ 放宽条件"
                            } else {
                                "还没有专注记录"
                            },
                            fontSize = 13.sp,
                            color = TextPlaceholder,
                        )
                    }
                }
            } else {
                // 🔴 分组行只算一次（原来写在 items() 里 = 每次重组都重新 groupBy + 分配）；
                //    并且必须带 key：没 key 时按索引认，删一条整列表重建、animateItem 也失效。
                val rows = remember(filtered) { recordsAsRows(filtered) }
                LazyColumn(contentPadding = PaddingValues(bottom = Dimens.ListBottomGap)) {
                    items(rows, key = { it.key }) { item ->
                        when (item) {
                            is RecordListItem.DayHeader -> Box(Modifier.animateItem()) {
                                DayHeader(item)
                            }
                            is RecordListItem.Entry -> Box(Modifier.animateItem()) {
                                Column {
                                    RecordRow(
                                        record = item.record,
                                        showTaskName = true,
                                        taskDeleted = item.record.taskId?.let { it !in liveTaskIds } == true,
                                        onEditDuration = { editingRecord = item.record },
                                        onDelete = {
                                            deletedRecord = item.record
                                            viewModel.deleteRecord(item.record)
                                        },
                                    )
                                    RecordDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        RecordUndoBar(
            visible = deletedRecord != null,
            accent = WarningAmber,
            seconds = 1,
            onUndo = {
                deletedRecord?.let { viewModel.restoreRecord(it) }
                deletedRecord = null
            },
            onExpire = { deletedRecord = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (taskPickerOpen) {
        TaskPickerSheet(
            tasks = tasks,
            selectedTaskId = selectedTaskId,
            onPick = { picked ->
                selectedTaskId = picked
                taskPickerOpen = false
            },
            onDismiss = { taskPickerOpen = false },
        )
    }

    editingRecord?.let { record ->
        DurationEditDialog(
            record = record,
            onDismiss = { editingRecord = null },
            onConfirm = { minutes ->
                viewModel.updateRecordDuration(record, minutes)
                editingRecord = null
            },
        )
    }
}

private fun recordsAsRows(records: List<FocusRecord>): List<RecordListItem> {
    val out = mutableListOf<RecordListItem>()
    val zone = ZoneId.systemDefault()
    records
        .groupBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate() }
        .forEach { (date, list) ->
            out += RecordListItem.DayHeader(date, list.sumOf { it.minutes })
            list.forEach { out += RecordListItem.Entry(it) }
        }
    return out
}

/**
 * 参数化筛选 chip：未激活 = 「任务：全部 ▾」描边态；激活 = 淡彩底 + 「✕」清除。
 * 点主体打开选择器，点 ✕ 清除该条件 —— 两个独立点按区
 * （M3：filter chip 的尾图标可用于打开菜单或移除，且清除要有独立触控区）。
 */
@Composable
private fun ParamChip(
    label: String,
    active: Boolean,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(ActivePalette.pillShape)
            .background(if (active) CardGreenLight else Color.Transparent)
            .border(
                1.dp,
                if (active) Color.Transparent else ActivePalette.borderColor,
                ActivePalette.pillShape,
            )
            .clickable(onClick = onOpen)
            .padding(start = 13.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) Primary else TextSecondary,
        )
        if (active) {
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", fontSize = 11.sp, color = Primary)
            }
        } else {
            Text("▾", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

/**
 * 任务选择面板：搜索框 + 单选列表（顶层任务 → 缩进的子任务）。
 * 「全部任务」排第一（= 清除任务筛选）。点选即关，无确认按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPickerSheet(
    tasks: List<FocusTask>,
    selectedTaskId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // 无搜索时按「顶层 → 它的子任务」分组排列；搜索时命中即平铺
    val ordered = remember(tasks, query) {
        val q = query.trim()
        if (q.isBlank()) {
            tasks.filter { it.parentId == null }
                .flatMap { top -> listOf(top) + tasks.filter { it.parentId == top.id } }
        } else {
            tasks.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PaperElevated) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("选择任务", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索任务", fontSize = 13.sp, color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = ActivePalette.tileShape,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    PickerRow(
                        name = "全部任务",
                        indent = false,
                        selected = selectedTaskId == null,
                        onClick = { onPick(null) },
                    )
                }
                items(ordered, key = { it.id }) { task ->
                    PickerRow(
                        name = task.name,
                        indent = task.parentId != null,
                        selected = selectedTaskId == task.id,
                        onClick = { onPick(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(name: String, indent: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ActivePalette.tileShape)
            .clickable(onClick = onClick)
            .padding(start = if (indent) 28.dp else 12.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Primary else TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
    }
}

private sealed interface RecordListItem {
    /** LazyColumn 稳定 key：日期头按日期、条目按记录 id */
    val key: Any

    data class DayHeader(val date: LocalDate, val minutes: Int) : RecordListItem {
        override val key: Any get() = "day-$date"
    }

    data class Entry(val record: FocusRecord) : RecordListItem {
        override val key: Any get() = "rec-${record.id}"
    }
}

@Composable
private fun DayHeader(item: RecordListItem.DayHeader) {
    val today = LocalDate.now()
    val md = "%02d-%02d".format(item.date.monthValue, item.date.dayOfMonth)
    val weekday = listOf("一", "二", "三", "四", "五", "六", "日")[item.date.dayOfWeek.value - 1]
    val relative = when (today.toEpochDay() - item.date.toEpochDay()) {
        0L -> "今天"
        1L -> "昨天"
        2L -> "前天"
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (relative != null) "$relative · $md 周$weekday" else "$md 周$weekday",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(formatMinutesHm(item.minutes), fontSize = 12.sp, color = Primary)
    }
}
