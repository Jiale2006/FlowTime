package com.example.flowtime.ui.stats

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.flowtime.ui.components.FlowAlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.CsvExporter
import com.example.flowtime.ui.components.DailyBarsStrip
import com.example.flowtime.ui.components.DonutChart
import com.example.flowtime.ui.components.DonutSlice
import com.example.flowtime.ui.components.HourBarsChart
import com.example.flowtime.ui.components.MonthHeatmap
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.WeeklyBarsPager
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.pressScale
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardCream
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.Dimens
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.SuccessGreen
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber

/**
 * 统计页（单页向下滚动）：
 * 范围切换（本周/本月/全部）+ 三卡总览 + 范围概览 + 专注趋势 + 时段分布
 * + 近 30 天热力图 + 分类分布 + 任务分布 + CSV 导出。
 * 三卡总览与导出保持全量语义，其余卡片跟随所选范围。
 */
@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val stats by viewModel.stats.collectAsState()
    val rangeStats by viewModel.rangeStats.collectAsState()
    val records by viewModel.records.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    var entrancePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrancePlayed = true }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(CsvExporter.Mode.DETAIL) }
    // 「保存到文件」：系统文件选择器挑位置后写入，不依赖任何接收应用
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val saved = CsvExporter.writeTo(
                context,
                uri,
                CsvExporter.toCsv(exportMode, records, tasks, categories),
            )
            Toast.makeText(
                context,
                if (saved) "CSV 已保存" else "保存失败，换个位置再试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val weekPages = remember(records) { viewModel.buildWeekPages(records) }
    val range = rangeStats.range

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "统计",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppViewModel.StatsRange.WEEK,
                        AppViewModel.StatsRange.MONTH,
                        AppViewModel.StatsRange.ALL,
                    ).forEach { item ->
                        RangeChip(
                            label = item.label,
                            selected = range == item,
                            onClick = { viewModel.updateStatsRange(item) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // 1. 三卡总览 + 今日目标进度（全量语义，与范围无关）
            Entrance(played = entrancePlayed, delayMillis = 0) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("总专注", stats.totalMinutes, StatValueKind.DURATION, Modifier.weight(1f))
                        StatCard("今日", stats.todayMinutes, StatValueKind.MINUTES, Modifier.weight(1f))
                        StatCard("连续", stats.streakDays, StatValueKind.DAYS, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    GoalCard(
                        todayMinutes = stats.todayMinutes,
                        goalMinutes = stats.goalMinutes,
                        bestStreakDays = stats.bestStreakDays,
                        goalHitDaysInWeek = stats.goalHitDaysInWeek,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 2. 范围概览：累计 / 日均 / 专注次数 / 单次最长
            Entrance(played = entrancePlayed, delayMillis = 80) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                rangeStats.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${rangeStats.activeDays} 天有记录",
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OverviewItem(
                                label = "累计专注",
                                value = formatMinutesHm(rangeStats.totalMinutes),
                                modifier = Modifier.weight(1f),
                            )
                            OverviewItem(
                                label = "日均",
                                value = formatMinutesHm(rangeStats.dailyAverageMinutes),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OverviewItem(
                                label = "专注次数",
                                value = "${rangeStats.sessionCount} 次",
                                modifier = Modifier.weight(1f),
                            )
                            OverviewItem(
                                label = "单次最长",
                                value = formatMinutesHm(rangeStats.longestSessionMinutes),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 3. 专注趋势：全部范围按周分页（左右滑看更早），本周/本月按天
            Entrance(played = entrancePlayed, delayMillis = 160) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "专注趋势",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (range == AppViewModel.StatsRange.ALL) {
                                    "全部记录 · 右滑看更早"
                                } else {
                                    "${range.label} · 按天"
                                },
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (range == AppViewModel.StatsRange.ALL) {
                            if (records.isEmpty()) {
                                Text("暂无记录，先去完成一次专注吧", fontSize = 13.sp, color = TextPlaceholder)
                            } else {
                                WeeklyBarsPager(pages = weekPages)
                            }
                        } else if (rangeStats.dailyDays.none { it.minutes > 0 }) {
                            Text("${range.label}暂无记录，先去完成一次专注吧", fontSize = 13.sp, color = TextPlaceholder)
                        } else {
                            DailyBarsStrip(days = rangeStats.dailyDays)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 4. 时段分布：24 小时，找出最专注的时段
            Entrance(played = entrancePlayed, delayMillis = 240) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "时段分布",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                peakHourText(rangeStats.hourMinutes),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Primary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        HourBarsChart(hourMinutes = rangeStats.hourMinutes)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 5. 分类分布：按大类（顶层任务）聚合
            val categoryEntries = rangeStats.categoryTotals.map {
                AppViewModel.TaskTotal(it.name, it.minutes)
            }
            val onlyUncategorized = categoryEntries.isEmpty() ||
                (categoryEntries.size == 1 && categoryEntries.first().name == "未分类")
            Entrance(played = entrancePlayed, delayMillis = 320) {
                DistributionCard(
                    title = "分类分布",
                    hint = "${range.label} · 按大类",
                    totals = if (onlyUncategorized) emptyList() else categoryEntries,
                    centerSubtext = "分类合计",
                    emptyText = "在任务详情页给任务添加小任务，它就成了大类，「分类分布」会自动汇总每个大类的时间占比",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 6. 任务分布：范围内按任务聚合
            Entrance(played = entrancePlayed, delayMillis = 400) {
                DistributionCard(
                    title = "任务分布",
                    hint = "${range.label} · 按任务",
                    totals = rangeStats.taskTotals,
                    centerSubtext = "总专注",
                    emptyText = "${range.label}暂无专注记录",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 7. 近 30 天热力图（固定窗口，范围外的日子淡显）
            Entrance(played = entrancePlayed, delayMillis = 480) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "近 30 天",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${rangeStats.heatmapActiveDays} 天有记录 · 共 ${formatMinutesHm(rangeStats.heatmapMinutes)}",
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        MonthHeatmap(cells = rangeStats.heatmap)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 8. CSV 导出（全量）
            Entrance(played = entrancePlayed, delayMillis = 560) {
                ProCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale()
                        .clickable(enabled = records.isNotEmpty()) { showExportDialog = true },
                ) {
                    Row(Modifier.padding(Dimens.RowPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("导出数据", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                if (records.isEmpty()) "暂无数据可导出" else "CSV 文件 · 共 ${records.size} 条记录",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showExportDialog) {
        FlowAlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出 CSV", fontWeight = FontWeight.Medium) },
            text = {
                Column {
                    Text("导出内容", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = exportMode == CsvExporter.Mode.DETAIL,
                            onClick = { exportMode = CsvExporter.Mode.DETAIL },
                            label = { Text("全部明细") },
                        )
                        FilterChip(
                            selected = exportMode == CsvExporter.Mode.CATEGORY,
                            onClick = { exportMode = CsvExporter.Mode.CATEGORY },
                            label = { Text("按大类汇总") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "「保存到文件」用系统文件选择器存到「下载」等位置；" +
                            "「分享」发给本机已安装的接收应用。",
                        fontSize = 12.sp,
                        color = TextPlaceholder,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveCsv.launch(CsvExporter.fileName(exportMode))
                        showExportDialog = false
                    },
                ) {
                    Text("保存到文件", color = Primary, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        CsvExporter.share(context, exportMode, records, tasks, categories)
                        showExportDialog = false
                    },
                ) {
                    Text("分享", color = Primary)
                }
            },
        )
    }
}

/** 今日目标进度：今日专注 / 设置里的「每日目标」（水彩渐变进度条） */
@Composable
private fun GoalCard(
    todayMinutes: Int,
    goalMinutes: Int,
    bestStreakDays: Int,
    goalHitDaysInWeek: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (goalMinutes <= 0) 0f else (todayMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    val done = goalMinutes > 0 && todayMinutes >= goalMinutes
    ProCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日目标",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatMinutesHm(todayMinutes)} / ${formatMinutesHm(goalMinutes)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (done) AccentGreen else Primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(ActivePalette.pillShape)
                    .background(CardCream),
            ) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(10.dp)
                            .clip(ActivePalette.pillShape)
                            .background(Brush.horizontalGradient(listOf(AccentGreen, Primary))),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (done) "今天的目标已完成" else "还差 ${formatMinutesHm((goalMinutes - todayMinutes).coerceAtLeast(0))}",
                fontSize = 12.sp,
                color = TextPlaceholder,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "最长连续 $bestStreakDays 天 · 近 7 天达成 $goalHitDaysInWeek/7",
                fontSize = 12.sp,
                color = TextPlaceholder,
            )
        }
    }
}

/** 范围切换 chip：选中 = 青绿淡彩底 + 主色字（与全站选中态一致） */
@Composable
private fun RangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ActivePalette.pillShape)
            .background(if (selected) CardGreenLight else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else DividerColor,
                shape = ActivePalette.pillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Primary else TextSecondary,
        )
    }
}

/** 概览小项：数值 + 标签（纸张淡彩底，柔和水彩感） */
@Composable
private fun OverviewItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ActivePalette.tileShape)
            .background(CardCream)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Primary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

/** 分布卡（任务分布 / 分类分布共用）：圆环 + 右侧明细列表 */
@Composable
private fun DistributionCard(
    title: String,
    hint: String,
    totals: List<AppViewModel.TaskTotal>,
    centerSubtext: String,
    emptyText: String,
) {
    ProCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(hint, fontSize = 11.sp, color = TextPlaceholder)
            }
            Spacer(Modifier.height(16.dp))
            if (totals.isEmpty()) {
                Text(emptyText, fontSize = 13.sp, color = TextPlaceholder)
            } else {
                val totalMinutes = totals.sumOf { it.minutes }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = totals.mapIndexed { index, item ->
                            DonutSlice(
                                color = DonutColors[index % DonutColors.size],
                                fraction = item.minutes.toFloat() / totalMinutes,
                            )
                        },
                        centerText = formatMinutesHm(totalMinutes),
                        centerSubtext = centerSubtext,
                        modifier = Modifier.weight(0.44f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(0.56f)) {
                        totals.forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(DonutColors[index % DonutColors.size]),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.name,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatMinutesHm(item.minutes),
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${item.minutes * 100 / totalMinutes}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Primary,
                                )
                            }
                            if (index < totals.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 「最专注 20:00-21:00 · 45 min」；无记录时返回空串 */
private fun peakHourText(hourMinutes: List<Int>): String {
    val peakHour = hourMinutes.indices.maxByOrNull { hourMinutes[it] } ?: return ""
    val peakMinutes = hourMinutes.getOrElse(peakHour) { 0 }
    if (peakMinutes <= 0) return ""
    val start = peakHour.toString().padStart(2, '0')
    val end = (peakHour + 1).toString().padStart(2, '0')
    return "最专注 $start:00-$end:00 · ${formatMinutesHm(peakMinutes)}"
}

/** 分区错峰入场：淡入 + 上滑（进入减速曲线） */
@Composable
private fun Entrance(
    played: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(420, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "entranceAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (played) 0.dp else 24.dp,
        animationSpec = tween(420, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "entranceOffset",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = offsetY.toPx() }) {
        content()
    }
}

private enum class StatValueKind { DURATION, MINUTES, DAYS }

/** 数字滚动计数卡片（从 0 滚动到目标值） */
@Composable
private fun StatCard(
    label: String,
    value: Int,
    kind: StatValueKind,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    val text = when (kind) {
        StatValueKind.DURATION, StatValueKind.MINUTES -> formatMinutesHm(animated)
        StatValueKind.DAYS -> "$animated 天"
    }
    ProCard(modifier = modifier) {
        Column(
            // ProCard 的内容 Box 是左上对齐：不撑满宽度的话，下面的
            // CenterHorizontally 只是在"自己那一小条"里居中，肉眼就是偏左
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Primary)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

/** 圆环图配色（按任务时长降序循环取色）；读取器形式，主题切换时取到当前色 */
private val DonutColors: List<Color>
    get() = listOf(Primary, AccentGreen, WarningAmber, Gold, PrimaryLight, SuccessGreen)
