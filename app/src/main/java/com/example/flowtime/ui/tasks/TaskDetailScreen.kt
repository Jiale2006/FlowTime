package com.example.flowtime.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.RecordLink
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.components.DonutChart
import com.example.flowtime.ui.components.DonutSlice
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.WeeklyBarsPager
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.DisabledGray
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.Dimens
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
            onConfirm = { minutes, dateMillis ->
                viewModel.addManualRecord(task, minutes, dateMillis)
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

/** 补录对话框：日期（今天/昨天/前天/自定义）+ 时长 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackfillDialog(
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int, dateMillis: Long) -> Unit,
) {
    val dateOptions = listOf("今天", "昨天", "前天", "自定义")
    var dateChoice by remember { mutableStateOf("昨天") }
    var customDate by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }

    val zone = ZoneId.systemDefault()
    val customParsed = customDate.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it.trim()) }.getOrNull()
    }
    val dateMillis: Long? = when (dateChoice) {
        "今天" -> LocalDate.now(zone).minusDays(0)
        "昨天" -> LocalDate.now(zone).minusDays(1)
        "前天" -> LocalDate.now(zone).minusDays(2)
        else -> customParsed
    }?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val minutes = minutesText.toIntOrNull() ?: 0
    val valid = dateMillis != null && minutes in 1..480

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补录时间", fontWeight = FontWeight.Medium) },
        text = {
            Column {
                Text("日期", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dateOptions.forEach { d ->
                        FilterChip(selected = dateChoice == d, onClick = { dateChoice = d }, label = { Text(d) })
                    }
                }
                if (dateChoice == "自定义") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDate,
                        onValueChange = { customDate = it.take(10) },
                        label = { Text("yyyy-MM-dd") },
                        singleLine = true,
                        isError = customDate.isNotBlank() && customParsed == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                    label = { Text("补录时长（分钟，1-480）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes, dateMillis!!) }, enabled = valid) {
                Text("补录", color = if (valid) Primary else DisabledGray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatEpoch(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d-%02d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
}

private fun formatDay(millis: Long): String {
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d-%02d".format(dt.monthValue, dt.dayOfMonth)
}
