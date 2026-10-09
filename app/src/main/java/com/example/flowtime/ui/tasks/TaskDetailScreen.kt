package com.example.flowtime.ui.tasks
import com.example.flowtime.ui.components.pastDayLabel
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.RecordLink
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.DonutChart
import com.example.flowtime.ui.components.DonutSlice
import com.example.flowtime.ui.components.BlurDialogBehind
import com.example.flowtime.ui.components.DayQuickChip
import com.example.flowtime.ui.components.DurationWheelBlock
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.HourMinuteWheel
import com.example.flowtime.ui.components.PastDateWheel
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.WeeklyBarsPager
import com.example.flowtime.ui.components.WheelHeight
import com.example.flowtime.ui.components.WheelSelectionBand
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.PaperElevated
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.SuccessGreen
import com.example.flowtime.ui.theme.TaskCardPalette
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


/**
 * 任务详情页：信息卡 + 小任务区（任何任务都能加小任务）+ 全部记录按周分页柱状图
 * + 记录列表 + 补录 + 所属分类入口。
 */
@Composable
fun TaskDetailScreen(
    viewModel: AppViewModel,
    taskId: String,
    onBack: () -> Unit,
    onStartFocus: () -> Unit,
    onOpenTask: (String) -> Unit = {},
) {
    val tasks by viewModel.tasks.collectAsState()
    val records by viewModel.records.collectAsState()
    val task = tasks.firstOrNull { it.id == taskId }
    val parentTask = task?.parentId?.let { parentId -> tasks.firstOrNull { it.id == parentId } }
    val children = tasks.filter { it.parentId == taskId }
    val stats = remember(records, task?.id) {
        task?.let { viewModel.taskStats(it, records) }
    }
    val weekPages = remember(records, task?.id) {
        val owner = task
        viewModel.buildWeekPages(
            if (owner == null) emptyList() else records.filter { RecordLink.belongsTo(it, owner) },
        )
    }
    val childMinutes = remember(records, children) {
        children.associate { child -> child.name to RecordLink.minutesOf(child, records) }
    }
    var showBackfill by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    val (cardColor, cardFg) = TaskCardPalette[(task?.colorIndex ?: 0) % TaskCardPalette.size]

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
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        TaskIcons.Glyph(index = task?.iconIndex ?: 0, size = 24.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            task?.name ?: "任务不存在",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                        )
                        if (task != null) {
                            Text(
                                if (children.isEmpty()) {
                                    "${parentTask?.name ?: "未分类"} · ${task.dateLabel}"
                                } else {
                                    "${parentTask?.name ?: "未分类"} · ${children.size} 个小任务"
                                },
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }

                // 所属分类入口（小任务 → 它的父任务）
                if (parentTask != null) {
                    Spacer(Modifier.height(14.dp))
                    ProCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(parentTask.id) },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "所属分类：${parentTask.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        DetailStat("总时长", formatMinutesHm(stats?.totalMinutes ?: 0), Modifier.weight(1f))
                        DetailStat("专注次数", "${stats?.sessionCount ?: 0} 次", Modifier.weight(1f))
                        DetailStat(
                            "最近专注",
                            stats?.lastFocusMillis?.let { formatDay(it) } ?: "-",
                            Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // 小任务：任何任务都可以加，加了它就成为分类/大类
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "小任务",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${children.size} 个", fontSize = 13.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.height(12.dp))

                        if (children.isNotEmpty()) {
                            val totalChildMinutes = childMinutes.values.sum()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (totalChildMinutes > 0) {
                                    DonutChart(
                                        slices = children.mapIndexed { index, child ->
                                            DonutSlice(
                                                color = CategoryColors[index % CategoryColors.size],
                                                fraction = (childMinutes[child.name] ?: 0).toFloat() /
                                                    totalChildMinutes,
                                            )
                                        },
                                        centerText = formatMinutesHm(totalChildMinutes),
                                        centerSubtext = "小任务",
                                        modifier = Modifier.weight(0.42f),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                Column(Modifier.weight(0.58f)) {
                                    children.forEach { child ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onOpenTask(child.id) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentGreen),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                child.name,
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                formatMinutesHm(childMinutes[child.name] ?: 0),
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        } else {
                            Text(
                                "还没有小任务。加上「做练习」「听网课」这类小项，就能分别统计它们的时间。",
                                fontSize = 13.sp,
                                color = TextPlaceholder,
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        Button(
                            onClick = { showEditor = true },
                            enabled = task != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .pressScale(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加小任务", fontSize = 15.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Text("专注趋势（右滑看更早）", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        if ((stats?.sessionCount ?: 0) == 0) {
                            Text("暂无记录", fontSize = 13.sp, color = TextPlaceholder)
                        } else {
                            WeeklyBarsPager(pages = weekPages)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Text("专注记录", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        val recent = stats?.recent.orEmpty()
                        if (recent.isEmpty()) {
                            Text("还没有专注记录，点击下方按钮开始第一次专注", fontSize = 13.sp, color = TextPlaceholder)
                        } else {
                            recent.forEachIndexed { index, record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AccentGreen),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        formatEpoch(record.endEpochMillis),
                                        fontSize = 13.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        formatMinutesHm(record.minutes),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                    )
                                }
                                if (index < recent.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(DividerColor))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(Dimens.CardPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showBackfill = true },
                    enabled = task != null,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeightHero).pressScale(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Primary),
                ) {
                    Text("补录时间", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        task?.let {
                            viewModel.selectTask(it.id)
                            viewModel.startFocus()
                            onStartFocus()
                        }
                    },
                    enabled = task != null,
                    modifier = Modifier.weight(1f).height(Dimens.ButtonHeightHero).pressScale(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("开始专注", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showBackfill && task != null) {
        BackfillDialog(
            onDismiss = { showBackfill = false },
            onConfirm = { minutes, endMillis ->
                viewModel.addManualRecord(task, minutes, endMillis)
                showBackfill = false
            },
        )
    }

    // 在这个任务下添加小任务（归属固定为当前任务）
    if (showEditor && task != null) {
        TaskEditorSheet(
            fixedParent = task,
            defaultColorIndex = children.size % TaskCardPalette.size,
            defaultIconIndex = children.size % TaskIcons.size,
            onDismiss = { showEditor = false },
            onConfirm = { draft ->
                viewModel.addTask(
                    name = draft.name,
                    dateLabel = todayLabel(),
                    colorIndex = draft.colorIndex,
                    iconIndex = draft.iconIndex,
                    parentId = task.id,
                )
                showEditor = false
            },
        )
    }
}

/** 小任务分布配色（与统计页同一套） */
private val CategoryColors = listOf(Primary, AccentGreen, WarningAmber, Gold, PrimaryLight, SuccessGreen)

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

/** 补录时长上限：8 小时，与 ViewModel 侧 coerceIn(1, 480) 对齐 */
private const val BackfillMaxMinutes = 480

/**
 * 补录：一步式底部弹层。
 *
 * 之前是三层弹窗（选结束时刻 → 选日期 → 选时长），点一次补录要点四次，
 * 而且第一层选的是**结束时刻**再往回倒推开始时间——选"昨天 23:00 + 2 小时"
 * 会把开始时刻推到今天 01:00，跨天的那段归属就说不清了。
 * 现在口径统一成「日期 + 开始时刻 + 时长」，结束时刻由程序算出来，
 * 跨天时预览会明确标"次日"。
 *
 * 未来时间钳制：选"今天"时，若开始+时长超过当前时刻，就地截断并在预览里说明，
 * 不让用户记出一条还没发生的专注。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackfillDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int, endMillis: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val nowMillis = remember { System.currentTimeMillis() }

    // 默认昨天 + 20:00 开始、30 分钟：多数人补录的是"昨天下午忘了记"的那一段
    var daysAgo by remember { mutableStateOf(1) }
    var showDateWheel by remember { mutableStateOf(false) }
    var startHour by remember { mutableStateOf(20) }
    var startMinute by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(30) }

    val date = today.minusDays(daysAgo.toLong())
    val rawStart = date.atStartOfDay(zone).plusHours(startHour.toLong()).plusMinutes(startMinute.toLong())
    val rawStartMillis = rawStart.toInstant().toEpochMilli()
    val rawEndMillis = rawStartMillis + duration * 60_000L

    // 今天不能补未来：结束超过"现在"就按时长回缩；起点本身就在未来的话回缩无意义
    val isToday = daysAgo == 0
    // 选"今天"时开始时刻轮直接钳到当下——HIG entering-data："design ways that make it
    // easy to provide it without making mistakes"：预防优于事后警告+禁用按钮。
    // 每次重组实时取值（滚轮每动一格都会重组），上限随钟表自然走
    val startCapMinutes = if (isToday) {
        val t = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
        t.hour * 60 + t.minute
    } else {
        23 * 60 + 59
    }
    // 日期切回"今天"时，把已经拨出去的未来起点拉回到当下
    LaunchedEffect(daysAgo) {
        if (daysAgo == 0) {
            val t = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone)
            if (startHour * 60 + startMinute > t.hour * 60 + t.minute) {
                startHour = t.hour
                startMinute = t.minute
            }
        }
    }
    val truncated = isToday && rawEndMillis > nowMillis
    val effectiveMinutes = when {
        !isToday -> duration
        // 起点已晚于现在（例如 00:10 选了 08:00）：整段时长都不可用
        rawStartMillis >= nowMillis -> 0
        else -> minOf(duration, ((nowMillis - rawStartMillis) / 60_000L).toInt())
    }
    val valid = effectiveMinutes >= 1
    val endMillis = if (truncated) rawStartMillis + effectiveMinutes * 60_000L else rawEndMillis

    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    val crossesDay = end.toLocalDate() != date
    val preview = when {
        !valid -> "开始时间晚于当前时刻，请改选更早的时间或换个日期"
        else -> buildString {
            append("%02d-%02d %02d:%02d".format(date.monthValue, date.dayOfMonth, startHour, startMinute))
            append(" → ")
            if (crossesDay) append("次日 ") else append("")
            append("%02d:%02d".format(end.hour, end.minute))
            append("（${formatMinutesHm(effectiveMinutes)}）")
            if (truncated) append(" · 已截断到当前时刻")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperElevated,
    ) {
        BlurDialogBehind(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.ListBottomGap),
        ) {
            Text("补录时间", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

            Spacer(Modifier.height(12.dp))
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

            // 「更多」就地展开日期滚轮，不再套一层弹窗（原来要点开再点确定）
            if (showDateWheel) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(WheelHeight), contentAlignment = Alignment.Center) {
                    WheelSelectionBand()
                    PastDateWheel(
                        daysAgo = daysAgo,
                        onDaysAgoChange = { daysAgo = it },
                        today = today,
                        modifier = Modifier.width(130.dp),
                        fadeColor = PaperElevated,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("开始时间", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            HourMinuteWheel(
                hours = startHour,
                minutes = startMinute,
                onChange = { h, m ->
                    startHour = h
                    startMinute = m
                },
                // 日期=今天时钳到当下时刻，拨不进未来；其余日期 00:00-23:59 全开放
                maxTotalMinutes = startCapMinutes,
                columnWidth = 62.dp,
                fadeColor = PaperElevated,
            )

            Spacer(Modifier.height(16.dp))
            Text("时长", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            DurationWheelBlock(
                minutes = duration,
                onChange = { duration = it.coerceIn(0, BackfillMaxMinutes) },
                maxTotalMinutes = BackfillMaxMinutes,
                fadeColor = PaperElevated,
            )

            Spacer(Modifier.height(10.dp))
            Text(preview, fontSize = 12.sp, color = TextSecondary)
            if (truncated) {
                Spacer(Modifier.height(4.dp))
                Text("不能记录还没发生的专注，已按当前时刻截断。", fontSize = 12.sp, color = WarningAmber)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(effectiveMinutes, endMillis) },
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
}

private fun formatEpoch(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
}

private fun formatDay(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d-%02d".format(dt.monthValue, dt.dayOfMonth)
}
