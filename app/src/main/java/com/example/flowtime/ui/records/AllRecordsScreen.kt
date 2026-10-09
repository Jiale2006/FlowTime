package com.example.flowtime.ui.records

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.RecordLink
import com.example.flowtime.data.RecordSource
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 全部记录页：跨任务的记录总览，倒序、按天分组。
 *
 * 筛选：来源（全部 / 计时 / 补录）。日期用**按天分组**呈现，比再塞一个日期区间选择器直观。
 * 交互与任务详情页的记录列表一致：长按删除（可撤销）、点按改时长（仅补录记录）。
 */
@Composable
fun AllRecordsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val records by viewModel.records.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    var filter by remember { mutableStateOf(RecordFilter.ALL) }
    var editingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    var deletedRecord by remember { mutableStateOf<FocusRecord?>(null) }

    val index = remember(tasks) { RecordLink.TaskIndex.of(tasks) }

    // 筛选 → 倒序 → 按结束日分组，摊平成「日期头 + 记录行」的线性列表（LazyColumn 好消费）
    val items = remember(records, filter) {
        val filtered = records
            .filter {
                when (filter) {
                    RecordFilter.ALL -> true
                    RecordFilter.TIMER -> it.recordSource == RecordSource.TIMER
                    RecordFilter.MANUAL -> it.recordSource == RecordSource.MANUAL
                }
            }
            .sortedByDescending { it.endEpochMillis }
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
                Text("${records.size} 条", fontSize = 12.sp, color = TextSecondary)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordFilter.entries.forEach { option ->
                    DayQuickChip(
                        label = option.label,
                        selected = filter == option,
                        onClick = { filter = option },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(Dimens.CardPadding)) {
                        Text(
                            when (filter) {
                                RecordFilter.ALL -> "还没有专注记录"
                                RecordFilter.TIMER -> "没有计时产生的记录"
                                RecordFilter.MANUAL -> "没有手动补录的记录"
                            },
                            fontSize = 13.sp,
                            color = TextPlaceholder,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Dimens.ListBottomGap)) {
                    items(items) { item ->
                        when (item) {
                            is RecordListItem.DayHeader -> DayHeader(item)
                            is RecordListItem.Entry -> {
                                RecordRow(
                                    record = item.record,
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

/** 来源筛选：全部 / 计时 / 补录 */
private enum class RecordFilter(val label: String) {
    ALL("全部"),
    TIMER("计时"),
    MANUAL("补录"),
}

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
