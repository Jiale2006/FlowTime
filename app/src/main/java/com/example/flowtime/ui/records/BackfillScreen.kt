package com.example.flowtime.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.flowtime.data.FocusTask
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.DurationWheelBlock
import com.example.flowtime.ui.components.HourMinuteWheel
import com.example.flowtime.ui.components.MonthCalendar
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pastDayLabel
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 页面开局用的初始值（已做过重叠避让） */
private data class Seed(
    val daysAgo: Int,
    val endHour: Int,
    val endMinute: Int,
    val durationMinutes: Int,
)

/** 「记到」标签里未分类的写法 */
private const val FREE_FOCUS_LABEL = "未分类（自由专注）"

/**
 * 补录时间（**独立页面**）。
 *
 * 2026-10-09 复盘：原来做成底部弹层是错的——内容有「日期 + 两个滚轮 + 预览」近 750dp，
 * 塞进弹层就得滚，而弹层还自带"从底部上浮 + 下拖关闭 + 点遮罩关闭"这些属于快捷面板的语义。
 * 补录是个**编辑动作**，该有自己的页面：直接出现（路由过渡本就是 None）、左上角返回、
 * 底部固定主按钮。
 *
 * 口径：**结束时刻是锚点**，日期 chip 选的是「结束日」，开始时刻 = 结束 − 时长。
 * 跨天溢出放行：开始时刻落到前一天不钳制、不警告。
 * 选「今天」时结束时刻不能拨到未来（滚轮上限 = 此刻，超出则结束钉在此刻、时长不变）。
 *
 * 主场景是「补昨天/前天忘掉的那一整段」→ 初始值固定落在**昨天**（见 [BackfillRules.defaultsFor]）；
 * 初始值撞上已有记录时**静默往后挪**，挪不动（超护栏）才退回显示警告（见 [BackfillRules.avoidOverlap]）。
 */
@Composable
fun BackfillScreen(
    viewModel: AppViewModel,
    initialTaskId: String?,
    onBack: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val records by viewModel.records.collectAsState()

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val nowMillis = remember { System.currentTimeMillis() }

    var taskId by remember { mutableStateOf(initialTaskId) }
    var showTaskPicker by remember { mutableStateOf(false) }
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
    var showDateWheel by remember(seed) { mutableStateOf(false) }
    var endHour by remember(seed) { mutableStateOf(seed.endHour) }
    var endMinute by remember(seed) { mutableStateOf(seed.endMinute) }
    var duration by remember(seed) { mutableStateOf(seed.durationMinutes) }

    val date = today.minusDays(daysAgo.toLong())
    val capMinutes = BackfillRules.endCapMinutes(date, nowMillis, zone)

    // 「更多」展开的月历：往回 12 个月（与统计页热力图能回看的范围一致），早→晚排列
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

    // 切到「今天」时，把已经拨到未来的结束时刻拉回此刻（否则滚轮和落库值会对不上）
    LaunchedEffect(daysAgo) {
        val cap = capMinutes
        if (endHour * 60 + endMinute > cap) {
            endHour = cap / 60
            endMinute = cap % 60
        }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding),
            ) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary,
                        )
                    }
                    Text("补录时间", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                Spacer(Modifier.height(6.dp))

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
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (showTaskPicker) "收起" else "更换",
                        fontSize = 13.sp,
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
                                showDateWheel = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DayQuickChip(
                        label = if (daysAgo > 2) pastDayLabel(today, daysAgo) else "更多",
                        selected = daysAgo > 2,
                        onClick = { showDateWheel = !showDateWheel },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 「更多」就地展开月历：比原来 60 天的滚轮好找，还能一眼看出哪天是空的
                if (showDateWheel) {
                    Spacer(Modifier.height(10.dp))
                    MonthCalendar(
                        months = calendarMonths,
                        minutesByDate = minutesByDate,
                        selected = date,
                        maxSelectable = today,
                        today = today,
                        onSelect = { picked ->
                            daysAgo = ChronoUnit.DAYS.between(picked, today).toInt().coerceAtLeast(0)
                            showDateWheel = false
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("结束时间", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                HourMinuteWheel(
                    hours = endHour,
                    minutes = endMinute,
                    onChange = { h, m ->
                        endHour = h
                        endMinute = m
                    },
                    // 今天只能到此刻；其它日期 00:00-23:59 全开放
                    maxTotalMinutes = capMinutes,
                    columnWidth = 62.dp,
                )
                if (daysAgo == 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("今天不能补还没发生的时间", fontSize = 11.sp, color = TextSecondary)
                }

                Spacer(Modifier.height(16.dp))
                Text("时长", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                DurationWheelBlock(
                    minutes = duration,
                    onChange = { duration = it.coerceIn(0, BackfillRules.MAX_MINUTES) },
                    maxTotalMinutes = BackfillRules.MAX_MINUTES,
                )

                Spacer(Modifier.height(10.dp))
                Text(preview, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
            }

            // 固定区：警告 + 主按钮，不跟着内容滚
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding)
                    .padding(bottom = Dimens.ListBottomGap),
            ) {
                if (conflicts.isNotEmpty()) {
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
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        viewModel.addManualRecord(pickedTask, span.startMillis, span.endMillis)
                        // 撤销条由上一页弹（状态在 ViewModel 里），这里直接退回去
                        onBack()
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(Dimens.ButtonHeightHero),
                    shape = ActivePalette.buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(
                        "补录",
                        fontSize = 16.sp,
                        color = if (valid) Color.White else DisabledGray,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
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
