package com.example.flowtime.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.FlowAlertDialog
import com.example.flowtime.ui.components.HourMinuteWheel
import com.example.flowtime.ui.components.MonthCalendar
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pastDayLabel
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 弹层开局用的初始值（已做过重叠避让） */
private data class Seed(
    val daysAgo: Int,
    val endHour: Int,
    val endMinute: Int,
    val durationMinutes: Int,
)

/** 哪个字段的滚轮选择器开着 */
private enum class FieldPicker { NONE, END_TIME, DURATION }

/** 「记到」标签里未分类的写法 */
private const val FREE_FOCUS_LABEL = "未分类（自由专注）"

/**
 * 补录时间（一步式底部弹窗）——任务详情页和首页共用。
 *
 * 2026-10-09 形态定稿（第三次也是最后一次）：**贴底弹窗 + 字段式输入**。
 * 走过的弯路值得记一笔——
 * 1. 最初两个 220dp 滚轮常驻，弹窗高 750dp，要滚到底才够得到按钮；
 * 2. 我误判成"容器错了"改成独立页面，但那只是绕路：
 *    **真正的病根是内容臃肿**（光滚轮就占 60% 高度）；
 * 3. 把滚轮收进字段的居中对话框后，弹窗只剩约 310dp，一屏足足的，
 *    既不会"整屏往上浮"，也不用滚。
 *
 * 口径：**结束时刻是锚点**，日期 chip 选的是「结束日」，开始时刻 = 结束 − 时长。
 * 跨天溢出放行：开始时刻落到前一天不钳制、不警告。
 * 选「今天」时结束时刻不能拨到未来（选择器上限 = 此刻，超出则结束钉在此刻、时长不变）。
 *
 * 主场景是「补昨天/前天忘掉的那一整段」→ 初始值固定落在**昨天**（见 [BackfillRules.defaultsFor]）；
 * 初始值撞上已有记录时**静默往后挪**，挪不动（超护栏）才退回显示警告（见 [BackfillRules.avoidOverlap]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillDialog(
    tasks: List<FocusTask>,
    initialTaskId: String?,
    records: List<FocusRecord>,
    onDismiss: () -> Unit,
    onConfirm: (task: FocusTask?, startMillis: Long, endMillis: Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val nowMillis = remember { System.currentTimeMillis() }

    var taskId by remember { mutableStateOf(initialTaskId) }
    var showTaskPicker by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(FieldPicker.NONE) }
    val pickedTask = tasks.firstOrNull { it.id == taskId }

    // 初始值：按该任务历史猜（日期固定昨天）；猜出的段撞上已有记录就静默往后挪（有护栏）。
    // **只在初始值上做一次**——用户拨过滚轮之后再自动挪，值会在手指底下自己跳。
    val seed = remember(taskId, records) {
        val d = BackfillRules.defaultsFor(records, taskId, zone)
        val day = today.minusDays(d.daysAgo.toLong())
        val raw = BackfillRules.resolve(day, d.endHour, d.endMinute, d.durationMinutes, nowMillis, zone)
        val adjusted = BackfillRules.avoidOverlap(raw, records, taskId, zone) ?: raw
        val end = Instant.ofEpochMilli(adjusted.endMillis).atZone(zone)
        Seed(d.daysAgo, end.hour, end.minute, adjusted.minutes)
    }
    var daysAgo by remember(seed) { mutableStateOf(seed.daysAgo) }
    var showCalendar by remember(seed) { mutableStateOf(false) }
    var endHour by remember(seed) { mutableStateOf(seed.endHour) }
    var endMinute by remember(seed) { mutableStateOf(seed.endMinute) }
    var duration by remember(seed) { mutableStateOf(seed.durationMinutes) }

    val date = today.minusDays(daysAgo.toLong())
    val capMinutes = BackfillRules.endCapMinutes(date, nowMillis, zone)

    // 切到「今天」时，把已经拨到未来的结束时刻拉回此刻（否则字段显示和落库值会对不上）
    LaunchedEffect(daysAgo) {
        val cap = capMinutes
        if (endHour * 60 + endMinute > cap) {
            endHour = cap / 60
            endMinute = cap % 60
        }
    }

    // 「更多」展开的月历：往回 12 个月，未来不可选
    val calendarMonths = remember(today) {
        val current = YearMonth.from(today)
        (0 until 12).map { current.minusMonths(it.toLong()) }.reversed()
    }
    // 每天已有的专注时长，用来给日历格子淡染。**按结束日聚合**——与全部统计口径一致
    val minutesByDate = remember(records, zone) {
        records
            .groupBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }
    }

    val span = BackfillRules.resolve(date, endHour, endMinute, duration, nowMillis, zone)
    val conflicts = BackfillRules.overlapping(records, taskId, span)
    val crosses = BackfillRules.crossesDay(span, zone)
    val valid = span.minutes >= 1

    val startZ = Instant.ofEpochMilli(span.startMillis).atZone(zone)
    val endZ = Instant.ofEpochMilli(span.endMillis).atZone(zone)
    val preview = "%02d-%02d %02d:%02d → %s%02d:%02d（%s）".format(
        startZ.monthValue, startZ.dayOfMonth, startZ.hour, startZ.minute,
        if (crosses) "次日 " else "",
        endZ.hour, endZ.minute,
        formatMinutesHm(span.minutes),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 弹层自己不吃手势：滚轮已挪进对话框，这里再关掉可避免误拖关闭
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = PaperElevated,
    ) {
        BlurDialogBehind(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp)
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("补录时间", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

            // 「记到「X」」可点击 → 就地展开任务列表，补录时能改归属
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ActivePalette.buttonShape)
                    .clickable { showTaskPicker = !showTaskPicker }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "记到「${pickedTask?.name ?: FREE_FOCUS_LABEL}」",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (showTaskPicker) "收起" else "更换",
                    fontSize = 12.sp,
                    color = Primary,
                )
            }

            if (showTaskPicker) {
                // 直接铺在同一层滚动里，不做嵌套滚动（嵌套同方向滚动会互相抢手势）
                TaskPickRow(
                    label = FREE_FOCUS_LABEL,
                    selected = taskId == null,
                    onClick = {
                        taskId = null
                        showTaskPicker = false
                    },
                )
                tasks.forEach { item ->
                    TaskPickRow(
                        label = item.name,
                        selected = taskId == item.id,
                        onClick = {
                            taskId = item.id
                            showTaskPicker = false
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 2).forEach { offset ->
                    DayQuickChip(
                        label = pastDayLabel(today, offset),
                        selected = daysAgo == offset,
                        onClick = {
                            daysAgo = offset
                            showCalendar = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                DayQuickChip(
                    label = if (daysAgo > 2) pastDayLabel(today, daysAgo) else "更多",
                    selected = daysAgo > 2,
                    onClick = { showCalendar = !showCalendar },
                    modifier = Modifier.weight(1f),
                )
            }

            // 「更多」就地展开月历（弹窗会长高，但仍在屏内）
            if (showCalendar) {
                Spacer(Modifier.height(12.dp))
                MonthCalendar(
                    months = calendarMonths,
                    minutesByDate = minutesByDate,
                    selected = date,
                    maxSelectable = today,
                    today = today,
                    onSelect = { picked ->
                        daysAgo = ChronoUnit.DAYS.between(picked, today).toInt().coerceAtLeast(0)
                        showCalendar = false
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            FieldRow(
                label = "结束时间",
                value = "%02d:%02d".format(endHour, endMinute),
                onClick = { picker = FieldPicker.END_TIME },
            )
            if (daysAgo == 0) {
                Text(
                    "今天不能补还没发生的时间",
                    fontSize = 11.sp,
                    color = TextPlaceholder,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            FieldRow(
                label = "时长",
                value = compactDuration(duration),
                onClick = { picker = FieldPicker.DURATION },
            )

            Spacer(Modifier.height(10.dp))
            Text(preview, fontSize = 12.sp, color = TextSecondary)

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val first = conflicts.first()
                val cs = Instant.ofEpochMilli(first.startEpochMillis).atZone(zone)
                val ce = Instant.ofEpochMilli(first.endEpochMillis).atZone(zone)
                Text(
                    "该时段与已有 ${conflicts.size} 条记录重叠" +
                        "（%02d:%02d-%02d:%02d，%s），确认后会重复计入".format(
                            cs.hour, cs.minute, ce.hour, ce.minute,
                            formatMinutesHm(first.minutes),
                        ),
                    fontSize = 12.sp,
                    color = WarningAmber,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(pickedTask, span.startMillis, span.endMillis) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeight),
                shape = ActivePalette.buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(
                    "补录",
                    fontSize = 15.sp,
                    color = if (valid) Color.White else DisabledGray,
                )
            }
        }
    }

    // 字段滚轮选择器：居中对话框（与贴底弹窗不同侧，层次一眼能看懂）
    when (picker) {
        FieldPicker.END_TIME -> WheelPickerDialog(
            title = "结束时间",
            initialMinutes = endHour * 60 + endMinute,
            maxTotalMinutes = capMinutes,
            summary = { total -> "%02d:%02d".format(total / 60, total % 60) },
            onDismiss = { picker = FieldPicker.NONE },
            onConfirm = { total ->
                endHour = total / 60
                endMinute = total % 60
                picker = FieldPicker.NONE
            },
        )

        FieldPicker.DURATION -> WheelPickerDialog(
            title = "时长",
            initialMinutes = duration,
            maxTotalMinutes = BackfillRules.MAX_MINUTES,
            summary = ::compactDuration,
            onDismiss = { picker = FieldPicker.NONE },
            onConfirm = { total ->
                duration = total.coerceIn(0, BackfillRules.MAX_MINUTES)
                picker = FieldPicker.NONE
            },
        )

        FieldPicker.NONE -> Unit
    }
}

/**
 * 滚轮选择对话框：时/分两列 + 确定。
 * 滚轮改动先攒在 [pending] 里，点「确定」才回写——不然拨动过程中外层字段会一直跳。
 */
@Composable
private fun WheelPickerDialog(
    title: String,
    initialMinutes: Int,
    maxTotalMinutes: Int,
    summary: (Int) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var pending by remember { mutableStateOf(initialMinutes.coerceIn(0, maxTotalMinutes)) }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Medium) },
        text = {
            Column {
                HourMinuteWheel(
                    hours = pending / 60,
                    minutes = pending % 60,
                    onChange = { h, m -> pending = (h * 60 + m).coerceIn(0, maxTotalMinutes) },
                    maxTotalMinutes = maxTotalMinutes,
                    columnWidth = 62.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(summary(pending), fontSize = 12.sp, color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending) }) {
                Text("确定", color = Primary, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 字段行：左标签、右值 + 箭头，整行可点 */
@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ActivePalette.buttonShape)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextPlaceholder,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 任务选择里的一行（含「未分类」），选中态用整行底色表示，与首页任务选择弹层一致 */
@Composable
private fun TaskPickRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ActivePalette.buttonShape)
            .background(if (selected) CardGreenLight else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = if (selected) Primary else TextPrimary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("当前", fontSize = 11.sp, color = Primary)
        }
    }
}

/** 字段里的时长写法：3h / 3h30m / 45min —— 比 formatMinutesHm 紧凑，一行放得下 */
private fun compactDuration(minutes: Int): String = when {
    minutes <= 0 -> "未设置"
    minutes % 60 == 0 -> "${minutes / 60}h"
    minutes < 60 -> "${minutes}min"
    else -> "${minutes / 60}h${minutes % 60}m"
}
