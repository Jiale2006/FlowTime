package com.example.flowtime.ui.records

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.RecordLink
import com.example.flowtime.data.StatsRules
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.WheelColumn
import com.example.flowtime.ui.components.WheelHeight
import com.example.flowtime.ui.components.WheelSelectionBand
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 记录页：跨任务的记录总览，倒序、按天分组。
 *
 * - 筛选收在右上角「筛选」里（任务名 + 时段），当前筛选状态显示在标题下面一行
 * - [initialTaskId] 非空 = 从任务详情进来：**默认只筛该任务**（含它的子任务），
 *   把搜索框清掉就能看全部任务
 * - 交互与任务详情页一致：长按删除（可撤销）、点按改时长（仅补录记录）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRecordsScreen(
    viewModel: AppViewModel,
    initialTaskId: String?,
    onBack: () -> Unit,
) {
    val records by viewModel.records.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val index = remember(tasks) { RecordLink.TaskIndex.of(tasks) }

    // 任务筛选默认填入"我从哪个任务点进来的"——于是它天然就是"看这个任务的记录"。
    // key 只挂 initialTaskId：挂上 tasks 的话，任务列表一变就会把用户敲的字冲掉。
    var taskQuery by remember(initialTaskId) {
        mutableStateOf(tasks.firstOrNull { it.id == initialTaskId }?.name.orEmpty())
    }
    var hourPreset by remember { mutableStateOf(StatsRules.HourPreset.ALL) }
    var customWindow by remember { mutableStateOf<StatsRules.HourWindow?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    var deletedRecord by remember { mutableStateOf<FocusRecord?>(null) }

    val query = taskQuery.trim()
    // 命中"任务名含关键字"的任务；记录若属于**命中任务本身**或**命中任务的子任务**都算命中
    // —— 所以搜「数学」能带出它下面「高数」「线代」的记录
    val matchedTaskIds = remember(tasks, query) {
        if (query.isBlank()) null
        else tasks.filter { it.name.contains(query, ignoreCase = true) }.map { it.id }.toSet()
    }
    val hourWindow = remember(hourPreset, customWindow) { customWindow ?: hourPreset.window }

    val hitsTask: (FocusRecord) -> Boolean = { record ->
        val matched = matchedTaskIds
        if (matched == null) {
            true
        } else {
            val owner = index.ownerOf(record)
            when {
                owner == null -> record.taskName.contains(query, ignoreCase = true)
                owner.id in matched -> true
                else -> owner.parentId?.let { it in matched } == true
            }
        }
    }

    val filtered = remember(records, matchedTaskIds, hourWindow, query) {
        records
            .filter { hitsTask(it) && StatsRules.inHourWindow(it, hourWindow, zone) }
            .sortedByDescending { it.endEpochMillis }
    }
    val filterActive = query.isNotBlank() || hourWindow != null

    // 摊平成「日期头 + 记录行」的线性列表（LazyColumn 好消费）
    val rows = remember(filtered, zone) {
        val out = mutableListOf<RecordListItem>()
        filtered
            .groupBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate() }
            .forEach { (date, list) ->
                out += RecordListItem.DayHeader(date, list.sumOf { it.minutes })
                list.forEach { out += RecordListItem.Entry(it) }
            }
        out
    }

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
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { sheetOpen = true }) {
                    Text(
                        if (filterActive) "筛选 ·" else "筛选",
                        fontSize = 13.sp,
                        fontWeight = if (filterActive) FontWeight.Medium else FontWeight.Normal,
                        color = Primary,
                    )
                }
            }

            // 当前筛选状态：收进按钮就容易"看不见自己筛了什么"，所以这里补一行摘要
            if (filterActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            if (query.isNotBlank()) append("任务「$query」")
                            hourWindow?.let {
                                if (isNotEmpty()) append(" · ")
                                append(hourWindowText(it))
                            }
                            append(" · 共 ${filtered.size} 条")
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            taskQuery = ""
                            hourPreset = StatsRules.HourPreset.ALL
                            customWindow = null
                        },
                    ) {
                        Text("清空", fontSize = 12.sp, color = Primary)
                    }
                }
            } else {
                Text(
                    "${filtered.size} 条",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))

            if (rows.isEmpty()) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(Dimens.CardPadding)) {
                        Text(
                            if (filterActive) "当前筛选下没有记录，换个条件试试" else "还没有专注记录",
                            fontSize = 13.sp,
                            color = TextPlaceholder,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Dimens.ListBottomGap)) {
                    items(rows) { item ->
                        when (item) {
                            is RecordListItem.DayHeader -> DayHeader(item)
                            is RecordListItem.Entry -> {
                                RecordRow(
                                    record = item.record,
                                    // 筛了任务名时在列表里标出任务，才分得清是哪条
                                    showTaskName = true,
                                    onEditDuration = { editingRecord = item.record },
                                    onDelete = {
                                        deletedRecord = item.record
                                        viewModel.deleteRecord(item.record.id)
                                    },
                                )
                                RecordDivider()
                            }
                        }
                    }
                }
            }
        }

        RecordUndoBar(
            visible = deletedRecord != null,
            onUndo = {
                deletedRecord?.let { viewModel.restoreRecord(it) }
                deletedRecord = null
            },
            onExpire = { deletedRecord = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (sheetOpen) {
        FilterSheet(
            taskQuery = taskQuery,
            onTaskQueryChange = { taskQuery = it },
            hourPreset = hourPreset,
            customWindow = customWindow,
            effectiveWindow = hourWindow,
            onPickPreset = {
                hourPreset = it
                customWindow = null
            },
            onCustomWindow = { customWindow = it },
            onClear = {
                taskQuery = ""
                hourPreset = StatsRules.HourPreset.ALL
                customWindow = null
            },
            onDismiss = { sheetOpen = false },
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

/**
 * 筛选面板：任务名搜索 + 时段（快捷档 / 自己拨起止小时）。
 * 改动**即时生效**（状态在页面上），关掉面板就能看到结果，所以只有一个「完成」按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    taskQuery: String,
    onTaskQueryChange: (String) -> Unit,
    hourPreset: StatsRules.HourPreset,
    customWindow: StatsRules.HourWindow?,
    effectiveWindow: StatsRules.HourWindow?,
    onPickPreset: (StatsRules.HourPreset) -> Unit,
    onCustomWindow: (StatsRules.HourWindow) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 拨轮时以"当前生效的区间"为基点，没有就默认整天
    val base = effectiveWindow ?: StatsRules.HourWindow(0, 23)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PaperElevated) {
        BlurDialogBehind(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("筛选", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(14.dp))

            Text("任务名称", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = taskQuery,
                onValueChange = onTaskQueryChange,
                singleLine = true,
                placeholder = {
                    Text("如「数学」，会连它的子任务一起带出来", fontSize = 13.sp, color = TextPlaceholder)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ActivePalette.tileShape,
            )

            Spacer(Modifier.height(16.dp))
            Text("时段", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsRules.HourPreset.entries.forEach { preset ->
                    DayQuickChip(
                        label = preset.label,
                        selected = customWindow == null && hourPreset == preset,
                        onClick = { onPickPreset(preset) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                if (customWindow == null) {
                    "也可以自己拨起止小时（起 > 止 表示跨午夜）"
                } else {
                    "自定义：${hourWindowText(base)}"
                },
                fontSize = 11.sp,
                color = TextPlaceholder,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.height(WheelHeight), contentAlignment = Alignment.Center) {
                    WheelSelectionBand()
                    HourWheel(base.startHour) { onCustomWindow(base.copy(startHour = it)) }
                }
                Text("→", fontSize = 14.sp, color = TextSecondary)
                Box(Modifier.height(WheelHeight), contentAlignment = Alignment.Center) {
                    WheelSelectionBand()
                    HourWheel(base.endHour) { onCustomWindow(base.copy(endHour = it)) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("清空筛选", color = TextSecondary, fontSize = 14.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeight),
                    shape = ActivePalette.buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("完成", fontSize = 15.sp)
                }
            }
        }
    }
}

/** 单个小时滚轮（00–23） */
@Composable
private fun HourWheel(value: Int, onPick: (Int) -> Unit) {
    val items = remember { (0..23).map { "%02d".format(it) } }
    WheelColumn(
        items = items,
        selectedIndex = value.coerceIn(0, 23),
        onIndexChange = onPick,
        modifier = Modifier.width(72.dp),
    )
}

/** 小时区间文案：「22:00–02:00」 */
private fun hourWindowText(window: StatsRules.HourWindow): String =
    "%02d:00–%02d:00".format(window.startHour, window.endHour)

private sealed interface RecordListItem {
    data class DayHeader(val date: LocalDate, val minutes: Int) : RecordListItem
    data class Entry(val record: FocusRecord) : RecordListItem
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
