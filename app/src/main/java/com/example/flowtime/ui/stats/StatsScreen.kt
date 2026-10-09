package com.example.flowtime.ui.stats

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import com.example.flowtime.data.StatsRange
import com.example.flowtime.data.StatsRules
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.CsvExporter
import com.example.flowtime.ui.components.DailyBarsStrip
import com.example.flowtime.ui.components.DonutChart
import com.example.flowtime.ui.components.DonutSlice
import com.example.flowtime.ui.components.HourBarsChart
import com.example.flowtime.ui.components.MonthHeatmapPager
import com.example.flowtime.ui.components.ProCard
import com.example.flowtime.ui.components.WeeklyBarsPager
import com.example.flowtime.ui.components.formatMinutesHm
import com.example.flowtime.ui.components.OvershootEasing
import com.example.flowtime.ui.components.rememberCountUp
import com.example.flowtime.ui.components.rememberCountUpFloat
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

    val weekPages = remember(records) { viewModel.buildWeekPages(records) }
    val range = rangeStats.range
    /** 导出**跟范围**：选了「本周」就只导本周，避免"统计看本周、导出却是全部" */
    val exportRecords = remember(records, range) { viewModel.recordsInRange(range) }

    // 「保存到文件」：系统文件选择器挑位置后写入，不依赖任何接收应用
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val saved = CsvExporter.writeTo(
                context,
                uri,
                CsvExporter.toCsv(exportMode, exportRecords, tasks, categories),
            )
            Toast.makeText(
                context,
                if (saved) "CSV 已保存" else "保存失败，换个位置再试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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
                    StatsRange.entries.forEach { item ->
                        RangeChip(
                            label = item.label,
                            selected = range == item,
                            onClick = { viewModel.updateStatsRange(item) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // 1. 三卡总览 + 今日目标进度
            // 第一张卡**跟范围**：原来它固定全量，于是切到"本周"时同屏出现
            // 「总专注 120h」和「累计专注 3h」——两个都叫专注总量，差 40 倍。
            // 「今日」「连续天数」不范围化：它们是天然的跨范围概念（口径原则里的成就/里程碑类）。
            Entrance(played = entrancePlayed, delayMillis = DelayTop) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            label = when (range) {
                                StatsRange.WEEK -> "本周专注"
                                StatsRange.MONTH -> "本月专注"
                                StatsRange.ALL -> "历史专注"
                            },
                            value = rangeStats.totalMinutes,
                            kind = StatValueKind.DURATION,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard("今日", stats.todayMinutes, StatValueKind.MINUTES, Modifier.weight(1f))
                        StatCard("连续天数", stats.streakDays, StatValueKind.DAYS, Modifier.weight(1f))
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

            // 2. 范围概览：日均 / 专注次数 / 单次最长（标题随范围变：本周 / 本月 / 全部概览）
            Entrance(played = entrancePlayed, delayMillis = DelayOverview) {
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
                                "${rememberCountUp(rangeStats.activeDays, delayMillis = DelayOverview + CountLag)} 天有记录",
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OverviewItem(
                                label = "日均",
                                // 范围内已过天数不足时不给数字：周一算出来的"日均"就是当天总量，是个假数字
                                value = rangeStats.dailyAverageMinutes,
                                modifier = Modifier.weight(1f),
                            )
                            OverviewItem(
                                label = "专注次数",
                                value = rangeStats.sessionCount,
                                kind = StatValueKind.COUNT,
                                modifier = Modifier.weight(1f),
                            )
                            OverviewItem(
                                label = "单次最长",
                                value = rangeStats.longestSessionMinutes,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 2.5 与上一区间对比（本周 vs 上周 / 本月 vs 上月）。
            // 「全部」档没有可比的上一区间，所以不显示——而不是显示一个假的 0。
            val previousMinutes = rangeStats.previousMinutes
            if (range != StatsRange.ALL && previousMinutes != null) {
                Entrance(played = entrancePlayed, delayMillis = DelayCompare) {
                    CompareCard(
                        currentMinutes = rangeStats.totalMinutes,
                        previousMinutes = previousMinutes,
                        previousLabel = rangeStats.previousLabel.orEmpty(),
                        changePercent = rangeStats.changePercent,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 3. 专注趋势：全部范围按周分页（左右滑看更早），本周/本月按天
            Entrance(played = entrancePlayed, delayMillis = DelayTrend) {
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
                                if (range == StatsRange.ALL) {
                                    "全部记录 · 右滑看更早"
                                } else {
                                    "${range.label} · 按天"
                                },
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (range == StatsRange.ALL) {
                            if (records.isEmpty()) {
                                Text("暂无记录，先去完成一次专注吧", fontSize = 13.sp, color = TextPlaceholder)
                            } else {
                                WeeklyBarsPager(
                                    pages = weekPages,
                                    countDelayMillis = DelayTrend + CountLag,
                                )
                            }
                        } else if (rangeStats.dailyDays.none { it.minutes > 0 }) {
                            Text("${range.label}暂无记录，先去完成一次专注吧", fontSize = 13.sp, color = TextPlaceholder)
                        } else {
                            DailyBarsStrip(days = rangeStats.dailyDays, countDelayMillis = DelayTrend + CountLag)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 4. 时段分布：24 小时，找出最专注的时段
            Entrance(played = entrancePlayed, delayMillis = DelayHours) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "时段分布",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            peakHourRange(rangeStats.hourMinutes)?.let { peakRange ->
                                Text(
                                    "最专注 $peakRange · ${
                                        formatMinutesHm(rangeStats.hourMinutes.maxOrNull() ?: 0)
                                    }",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HourBarsChart(
                            hourMinutes = rangeStats.hourMinutes,
                            startDelayMillis = DelayHours + CountLag,
                        )
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

            // 6.5 单次时长分布 + 历史最强一天
            Entrance(played = entrancePlayed, delayMillis = 440) {
                DurationBucketsCard(
                    buckets = rangeStats.durationBuckets,
                    hint = "${range.label} · 单次时长",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 成就类数据：**全量**（口径原则里的"成就/里程碑"），不跟范围
            if (rangeStats.topDays.isNotEmpty()) {
                Entrance(played = entrancePlayed, delayMillis = 460) {
                    TopDaysCard(days = rangeStats.topDays)
                }
                Spacer(Modifier.height(16.dp))
            }

            // 7. 月份热力图：可左右滑动看更早的月份
            Entrance(played = entrancePlayed, delayMillis = 480) {
                ProCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Dimens.CardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "专注热力图",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (rangeStats.heatMonths.size <= 1) {
                                Text(
                                    "${rangeStats.heatMonths.firstOrNull()?.activeDays ?: 0} 天有记录",
                                    fontSize = 11.sp,
                                    color = TextPlaceholder,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        MonthHeatmapPager(months = rangeStats.heatMonths)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 8. CSV 导出（跟范围：选「本周」就只导本周）
            Entrance(played = entrancePlayed, delayMillis = 560) {
                ProCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale()
                        .clickable(enabled = exportRecords.isNotEmpty()) { showExportDialog = true },
                ) {
                    Row(Modifier.padding(Dimens.RowPadding), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("导出数据", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                if (exportRecords.isEmpty()) {
                                    "${range.label}暂无数据可导出"
                                } else {
                                    "CSV · ${range.label} ${exportRecords.size} 条记录"
                                },
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
                        CsvExporter.share(context, exportMode, exportRecords, tasks, categories)
                        showExportDialog = false
                    },
                ) {
                    Text("分享", color = Primary)
                }
            },
        )
    }
}

/**
 * 有滚动数字的分区的入场延迟（错峰滑入，从上到下依次进场）。
 * 卡内数字滚动 / 柱子生长 = 该分区延迟 + [CountLag]，这样是「卡片滑到位时数字还在往上数」；
 * 两者若不一致，靠下的卡片会出现"数字数完了卡片才出现"的脱节感。
 * 其余分区没有数字，延迟仍写字面量。
 */
private const val DelayTop = 0
private const val DelayOverview = 80
private const val DelayCompare = 120
private const val DelayTrend = 160
private const val DelayHours = 240
private const val CountLag = 140

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
    // 今日目标只动进度条：数字保持静态（滚动会让"还差多少"跟着跳，读着累）。
    // 进度条与上方数字滚动**同节奏**：同为 800ms + 同一延迟，缓动换成带过冲的
    // OvershootEasing（先冲过头一点再回落）——用户要求"和数字增加的时长一样"。
    val animatedProgress = rememberCountUpFloat(
        value = progress,
        durationMillis = 800,
        delayMillis = DelayTop + CountLag,
        easing = OvershootEasing,
    )
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
                if (animatedProgress > 0f) {
                    Box(
                        Modifier
                            // spring 会 overshoot，fraction 越界会崩，必须钳回 0..1
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
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
                // 「最近 7 天」是**滚动**窗口，不是本周——同屏有「本周/本月」档位，写"近 7 天"容易被读成本周
                "最长连续 $bestStreakDays 天 · 最近 7 天达成 $goalHitDaysInWeek/7",
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

/** 概览小项：数值 + 标签（纸张淡彩底，柔和水彩感）。数值同样从 0 数上去 */
@Composable
private fun OverviewItem(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    kind: StatValueKind = StatValueKind.DURATION,
    countDelayMillis: Int = DelayOverview + CountLag,
) {
    val animated = rememberCountUp(value ?: 0, delayMillis = countDelayMillis)
    Column(
        modifier = modifier
            .clip(ActivePalette.tileShape)
            .background(CardCream)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 值为 null = 这个口径本身不成立（如范围刚开始算不出日均），不给数字，也就不滚动
        Text(
            if (value == null) "—" else formatStatValue(kind, animated),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Primary,
        )
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

private val WeekdayShort = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 与上一区间对比卡（本周 vs 上周 / 本月 vs 上月）。
 * 上一区间为 0 时不编造百分比——"从 0 涨到 100%"没有意义，直接说明"上一区间没有记录"。
 */
@Composable
private fun CompareCard(
    currentMinutes: Int,
    previousMinutes: Int,
    previousLabel: String,
    changePercent: Int?,
) {
    val accent = if (currentMinutes >= previousMinutes) AccentGreen else WarningAmber
    ProCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "与$previousLabel 对比",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        changePercent == null -> "上一区间无记录"
                        changePercent > 0 -> "↑ $changePercent%"
                        changePercent < 0 -> "↓ ${-changePercent}%"
                        else -> "持平"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (changePercent == null) TextPlaceholder else accent,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewItem(
                    label = "本期",
                    value = currentMinutes,
                    modifier = Modifier.weight(1f),
                    countDelayMillis = DelayCompare + CountLag,
                )
                OverviewItem(
                    label = previousLabel,
                    value = previousMinutes,
                    modifier = Modifier.weight(1f),
                    countDelayMillis = DelayCompare + CountLag,
                )
            }
        }
    }
}

/** 单次时长分布（跟范围）：一眼看出自己主要在"短平快"还是"长时段" */
@Composable
private fun DurationBucketsCard(buckets: List<StatsRules.DurationBucket>, hint: String) {
    val total = buckets.sumOf { it.count }
    ProCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "单次时长分布",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(hint, fontSize = 11.sp, color = TextPlaceholder)
            }
            Spacer(Modifier.height(14.dp))
            if (total == 0) {
                Text("${hint.substringBefore(" ·")}暂无记录", fontSize = 13.sp, color = TextPlaceholder)
            } else {
                val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)
                buckets.forEach { bucket ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bucket.label,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.width(56.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CardCream),
                        ) {
                            if (bucket.count > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(bucket.count.toFloat() / maxCount)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Primary.copy(alpha = 0.7f)),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${bucket.count} 次",
                            fontSize = 11.sp,
                            color = TextPlaceholder,
                            modifier = Modifier.width(42.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** 历史最强的一天 Top N —— **全量**口径（准则里的"成就/里程碑类"不跟范围） */
@Composable
private fun TopDaysCard(days: List<StatsRules.TopDay>) {
    ProCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最强的一天",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text("历史 · 前 ${days.size}", fontSize = 11.sp, color = TextPlaceholder)
            }
            Spacer(Modifier.height(12.dp))
            days.forEachIndexed { index, day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) Gold else CardCream),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = 11.sp,
                            color = if (index == 0) Color.White else TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "%d 月 %d 日 · 周%s".format(
                            day.date.monthValue,
                            day.date.dayOfMonth,
                            WeekdayShort[day.date.dayOfWeek.value - 1],
                        ),
                        fontSize = 13.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMinutesHm(day.minutes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Primary,
                    )
                }
                if (index < days.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * 最专注的时段「20:00-21:00」；无记录返回 null（此时整段文案不显示）。
 * 时长数字由调用方用 [rememberCountUp] 滚动，所以这里只回时段。
 */
private fun peakHourRange(hourMinutes: List<Int>): String? {
    val peakHour = hourMinutes.indices.maxByOrNull { hourMinutes[it] } ?: return null
    if (hourMinutes.getOrElse(peakHour) { 0 } <= 0) return null
    return "%02d:00-%02d:00".format(peakHour, peakHour + 1)
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

private enum class StatValueKind { DURATION, MINUTES, DAYS, COUNT }

/** 数值 → 文案。滚动过程中也走它，保证中间帧的格式和终值一致（不然会看到 "1440min" 突然变 "24h"） */
private fun formatStatValue(kind: StatValueKind, value: Int): String = when (kind) {
    StatValueKind.DURATION, StatValueKind.MINUTES -> formatMinutesHm(value)
    StatValueKind.DAYS -> "$value 天"
    StatValueKind.COUNT -> "$value 次"
}

/** 数字滚动计数卡片（进入时从 0 滚动到目标值） */
@Composable
private fun StatCard(
    label: String,
    value: Int,
    kind: StatValueKind,
    modifier: Modifier = Modifier,
    countDelayMillis: Int = DelayTop + CountLag,
) {
    val animated = rememberCountUp(value, delayMillis = countDelayMillis)
    val text = formatStatValue(kind, animated)
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
