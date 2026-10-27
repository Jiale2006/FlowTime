package com.example.flowtime.ui.stats

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
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
import com.example.flowtime.ui.components.formatMinutesCompact
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
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.SuccessGreen
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WarningAmber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * 统计页（单页向下滚动）：
 * 范围切换（本周/本月/全部）+ 三卡总览 + 范围概览 + 专注趋势 + 时段分布
 * + 任务分布（卡片自带 今天/本周/本月 chip，今天档可右滑看前一天；页面选「全部」时只剩「全部」）
 * + 单次时长 + 最强一天 + 月份热力图 + CSV 导出。
 * 「分类分布」已整块删除（2026-10-23：他的任务是"顶层任务"结构，该维度只会显示「未分类」）。
 */
@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val rangeStats by viewModel.rangeStats.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var entrancePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrancePlayed = true }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(CsvExporter.Mode.DETAIL) }

    val range = rangeStats.range
    /** 导出**跟范围**：选了「本周」就只导本周，避免"统计看本周、导出却是全部" */
    val exportRecords = remember(records, range) { viewModel.recordsInRange(range) }

    /**
     * 任务分布自己的时间档 + 每档各记各的锚点。
     *
     * 为什么不并进页面顶部的范围 chip：那个 chip 管着概览/趋势/时段/热力图，
     * 而任务分布他要求「三档都能左右翻，空档也要能滑过去看」，粒度不一样。
     *
     * 🔴 **每档独立存锚点**：原来三档共用一个 `distAnchor`，在「今天」翻到 8 月 1 日
     *    再切「本周」就会跟到 8 月 1 日那周 —— 像是被拖走了。独立之后，切档各自
     *    回到自己上次看的位置（没翻过就是今天所在的日/周/月）。
     */
    // 初始值就跟页面范围对齐：范围跨启动记在 settings 里，直接选「全部」时
    // 不要先渲染一帧「今天」+三个 chip 再收成「全部」
    var distRange by remember {
        mutableStateOf(
            if (range == StatsRange.ALL) TaskDistRange.ALL else TaskDistRange.TODAY,
        )
    }
    val distAnchors = remember { mutableStateMapOf<TaskDistRange, LocalDate>() }
    val distAnchor = distAnchors[distRange] ?: LocalDate.now()
    // 页面选「全部」时，任务分布也只给「全部」（没有上一个区间可翻）；回到本周/本月时回到今天档
    LaunchedEffect(range) {
        if (range == StatsRange.ALL) {
            distRange = TaskDistRange.ALL
        } else if (distRange == TaskDistRange.ALL) {
            distRange = TaskDistRange.TODAY
        }
    }
    val distOptions = if (range == StatsRange.ALL) {
        listOf(TaskDistRange.ALL)
    } else {
        listOf(TaskDistRange.TODAY, TaskDistRange.WEEK, TaskDistRange.MONTH)
    }
    val distTotals = remember(records, tasks, distRange, distAnchor) {
        taskTotalsFor(records, tasks, distRange, distAnchor)
    }

    // 「保存到文件」：系统文件选择器挑位置后写入，不依赖任何接收应用
    val saveCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val saved = CsvExporter.writeTo(
                context,
                uri,
                CsvExporter.toCsv(exportMode, exportRecords, tasks),
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

            // 1. 总览 + 今日目标进度
            // 第一张卡**跟范围**：原来它固定全量，于是切到"本周"时同屏出现
            // 「总专注 120h」和「累计专注 3h」——两个都叫专注总量，差 40 倍。
            // 「今日」「连续天数」不范围化：它们是天然的跨范围概念（口径原则里的成就/里程碑类）。
            //
            // 🔴 2026-10-25 去重 + 拉主次：原来三张等权（今日 / 本周 / 连续天数），
            //    而「今日」又和目标卡右上角的 "30 分钟 / 2 小时" 说的是同一件事 ——
            //    一个数字在一屏出现两次，三张卡还一样大，等于没有主次。
            //    现在：今日做**主指标**（更宽、字更大），本周与连续天数做次级；
            //    目标卡只留目标值和进度条，不再重复今日读数。
            Entrance(played = entrancePlayed, delayMillis = DelayTop) {
                Column {
                    // 高度对齐：Row 拿最高的那张（今日 26sp）当基准，其余撑到同高，
                    // 否则三张卡各按自己内容撑高，右侧「本月」会比「今日专注」矮一截
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            label = "今日专注",
                            value = stats.todayMinutes,
                            kind = StatValueKind.MINUTES,
                            modifier = Modifier.weight(1.4f).fillMaxHeight(),
                            valueSize = 26.sp,
                        )
                        StatCard(
                            label = when (range) {
                                StatsRange.WEEK -> "本周"
                                StatsRange.MONTH -> "本月"
                                StatsRange.ALL -> "历史"
                            },
                            value = rangeStats.totalMinutes,
                            kind = StatValueKind.DURATION,
                            // 历史值可能是 "1234h 30min"（102.5dp）：原来 1f 只给 104.8dp，贴边到只剩 2dp
                            modifier = Modifier.weight(1.3f).fillMaxHeight(),
                        )
                        StatCard(
                            label = "连续天数",
                            value = stats.streakDays,
                            kind = StatValueKind.DAYS,
                            modifier = Modifier.weight(0.9f).fillMaxHeight(),
                        )
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
            // 「全部」档没有上一区间：用 AnimatedVisibility 让它带着高度平滑地收上去，
            // 而不是整张卡凭空消失、下面所有内容瞬间上移一张卡。
            AnimatedVisibility(
                visible = range != StatsRange.ALL && previousMinutes != null,
                enter = fadeIn(tween(Motion.DurationPress)) +
                    expandVertically(tween(Motion.DurationPress)),
                exit = fadeOut(tween(Motion.DurationPress)) +
                    shrinkVertically(tween(Motion.DurationPress)),
            ) {
                Column {
                    Entrance(played = entrancePlayed, delayMillis = DelayCompare) {
                        CompareCard(
                            currentMinutes = rangeStats.totalMinutes,
                            previousMinutes = previousMinutes ?: 0,
                            previousLabel = rangeStats.previousLabel.orEmpty(),
                            changePercent = rangeStats.changePercent,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
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
                                    "全部记录 · 左右滑动看更早"
                                } else {
                                    "${range.label} · 按天"
                                },
                                fontSize = 11.sp,
                                color = TextPlaceholder,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // 三档统一用可按天横向滑动的条：全部也能一路拨到最早一条记录，
                        // 不再走「按周分页」（分页手势与纵向滚动打架、且方向反直觉）
                        if (rangeStats.dailyDays.none { it.minutes > 0 }) {
                            Text(
                                if (range == StatsRange.ALL) {
                                    "暂无记录，先去完成一次专注吧"
                                } else {
                                    "${range.label}暂无记录，先去完成一次专注吧"
                                },
                                fontSize = 13.sp,
                                color = TextPlaceholder,
                            )
                        } else {
                            DailyBarsStrip(
                                days = rangeStats.dailyDays,
                                countDelayMillis = DelayTrend + CountLag,
                                // 本月/全部都每天标日期（本月只有 31 根，跳着标就看不出是哪天）
                                labelEveryBar = range != StatsRange.WEEK,
                            )
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
                                        formatMinutesCompact(rangeStats.hourMinutes.maxOrNull() ?: 0)
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

            // 5. 任务分布：卡片自带时间 chip（今天/本周/本月），三档都能左右翻
            //    （「分类分布」已按他要求整块删掉，2026-10-23）
            Entrance(played = entrancePlayed, delayMillis = 320) {
                DistributionCard(
                    title = "任务分布",
                    hint = taskDistHint(distRange, distAnchor),
                    totals = distTotals,
                    centerSubtext = when (distRange) {
                        TaskDistRange.ALL -> "全部专注"
                        TaskDistRange.TODAY -> "当日专注"
                        TaskDistRange.WEEK -> "本周专注"
                        TaskDistRange.MONTH -> "本月专注"
                    },
                    emptyText = if (distRange == TaskDistRange.ALL) {
                        "还没有专注记录"
                    } else {
                        "${taskDistPeriodLabel(distRange, distAnchor)}没有专注记录 · 继续左右滑看看"
                    },
                    headerExtra = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            distOptions.forEach { item ->
                                RangeChip(
                                    label = item.label,
                                    selected = distRange == item,
                                    onClick = { distRange = item },
                                )
                            }
                        }
                    },
                    // 全部档没有可翻的上一段：不给于势，免得“滑一下什么都没发生”
                    onSwipeBack = if (distRange == TaskDistRange.ALL) null else {
                        { distAnchors[distRange] = shiftAnchor(distAnchor, distRange, -1) }
                    },
                    onSwipeForward = if (distRange == TaskDistRange.ALL) null else {
                        { distAnchors[distRange] = shiftAnchor(distAnchor, distRange, +1) }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            // 6. 单次时长分布 + 历史最强一天
            Entrance(played = entrancePlayed, delayMillis = 400) {
                DurationBucketsCard(
                    buckets = rangeStats.durationBuckets,
                    hint = "${range.label} · 单次时长",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 成就类数据：**全量**（口径原则里的"成就/里程碑"），不跟范围
            if (rangeStats.topDays.isNotEmpty()) {
                Entrance(played = entrancePlayed, delayMillis = 440) {
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
                        CsvExporter.share(context, exportMode, exportRecords, tasks)
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
 * 达成扫光：亮带位置由 [p]（0 → 1）驱动，从左扫到右。
 * 亮带完全移出画面时返回全透明（两端 start==end 会退化，必须保护）。
 */
private fun goalShineBrush(p: Float): Brush {
    val band = 0.3f
    val start = (p * (1 + band) - band).coerceIn(0f, 1f)
    val end = (p * (1 + band)).coerceIn(0f, 1f)
    if (end - start < 0.002f) {
        return Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    return Brush.linearGradient(
        0f to Color.Transparent,
        start to Color.Transparent,
        (start + end) / 2f to Color.White.copy(alpha = 0.55f),
        end to Color.Transparent,
        1f to Color.Transparent,
    )
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
    // 达成瞬间：进度条宽度已钳在满格，过冲看不见 —— 达成感换「高光扫过」这个维度给。
    // 条约在 140 + 800 = 940ms 长满，扫光随后起跑；刚进页面就已经达成时也照样扫一次。
    val shine = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(1000, delayMillis = 980, easing = FastOutSlowInEasing))
        } else {
            shine.snapTo(1f)
        }
    }
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
                    // 🔴 只写目标值：今日读数已经在上方的主卡里，
                    //    原来这里也写 "30 分钟 / 2 小时"，同一屏说了两遍。
                    "目标 ${formatMinutesCompact(goalMinutes)}",
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
                            // 过冲缓动会超过 1，fraction 越界会崩，必须钳回 0..1
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(10.dp)
                            .clip(ActivePalette.pillShape)
                            .background(Brush.horizontalGradient(listOf(AccentGreen, Primary))),
                    )
                }
                if (done) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(goalShineBrush(shine.value)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val noteColor by animateColorAsState(
                targetValue = if (done) AccentGreen else TextPlaceholder,
                animationSpec = tween(300),
                label = "goalNoteColor",
            )
            Crossfade(targetState = done, label = "goalNote") { isDone ->
                Text(
                    if (isDone) {
                        "今天的目标已完成"
                    } else {
                        "还差 ${formatMinutesCompact((goalMinutes - todayMinutes).coerceAtLeast(0))}"
                    },
                    fontSize = 12.sp,
                    color = noteColor,
                )
            }
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

/**
 * 分布卡（任务分布）：圆环 + 右侧明细列表。
 *
 * [headerExtra] 放右上角的时间 chip；[onSwipeBack] / [onSwipeForward] 是横向手势，
 * 只在「今天」档由调用方传（周/月是聚合值，切"前一天"没有意义）。
 */
@Composable
private fun DistributionCard(
    title: String,
    hint: String,
    totals: List<AppViewModel.TaskTotal>,
    centerSubtext: String,
    emptyText: String,
    headerExtra: (@Composable () -> Unit)? = null,
    onSwipeBack: (() -> Unit)? = null,
    onSwipeForward: (() -> Unit)? = null,
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
                headerExtra?.invoke()
            }
            Spacer(Modifier.height(6.dp))
            Text(hint, fontSize = 11.sp, color = TextPlaceholder)
            Spacer(Modifier.height(14.dp))

            // 🔴 回调必须用 rememberUpdatedState 固定住：lambda 每次重组都是新实例，
            // 直接拿它当 pointerInput 的 key 会让手势检测器反复重启 ——
            // 表现就是"滑到昨天之后滑不回来"（2026-10-23 他报的）。
            val back by rememberUpdatedState(onSwipeBack)
            val forward by rememberUpdatedState(onSwipeForward)
            var dragRaw by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            // 跟手位移：拖动时只走实际位移的 35%，松手弹回 0
            val settle by animateFloatAsState(
                targetValue = if (dragging) dragRaw else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "distSwipeOffset",
            )

            val swipes = if (onSwipeBack != null || onSwipeForward != null) {
                Modifier
                    // 空白日内容只有一行字：不给最小高度，可滑区域小到几乎碰不到
                    .heightIn(min = 140.dp)
                    .pointerInput(Unit) {
                        val limit = 56.dp.toPx()
                        val threshold = 40.dp.toPx()
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                acc = 0f
                                dragging = true
                            },
                            onHorizontalDrag = { _, amount ->
                                acc += amount
                                dragRaw = (dragRaw + amount * 0.35f).coerceIn(-limit, limit)
                            },
                            onDragEnd = {
                                dragging = false
                                dragRaw = 0f
                                if (abs(acc) > threshold) {
                                    // 右滑 = 前一天，左滑 = 后一天
                                    if (acc > 0) back?.invoke() else forward?.invoke()
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                dragRaw = 0f
                            },
                        )
                    }
            } else {
                Modifier
            }
            Box(
                swipes
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = settle
                        // 拉得越远越淡，切换时才不会像"跳一下"
                        alpha = 1f - (abs(settle) / (240.dp.toPx())).coerceIn(0f, 0.3f)
                    },
            ) {
                // 空档也画一个灰环占位：只留一行字的话卡片会突然变矮，
                // 左右滑过去再滑回来高度一跳一跳，手感完全不像同一张卡
                val totalMinutes = totals.sumOf { it.minutes }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        slices = if (totals.isEmpty()) {
                            listOf(DonutSlice(color = DividerColor, fraction = 1f))
                        } else {
                            totals.mapIndexed { index, item ->
                                DonutSlice(
                                    color = DonutColors[index % DonutColors.size],
                                    fraction = item.minutes.toFloat() / totalMinutes,
                                )
                            }
                        },
                        centerText = if (totals.isEmpty()) "0" else formatMinutesCompact(totalMinutes),
                        centerSubtext = centerSubtext,
                        modifier = Modifier.weight(0.44f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(0.56f)) {
                        if (totals.isEmpty()) {
                            Text(emptyText, fontSize = 13.sp, color = TextPlaceholder)
                        } else {
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
                                        formatMinutesCompact(item.minutes),
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
    // 🔴 上一区间没记录时**不摆两张 0**（2026-10-25）：原来这种情况仍然占一整张卡、
    //    里面两个大数字一个 30 分钟一个 0 分钟，信息量只有一句话。空态就该是一行。
    //    「本期」也不必在这重复 —— 它就在上方的范围主卡里。
    if (changePercent == null) {
        ProCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = Dimens.CardPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "与$previousLabel 对比",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "上一区间无记录，没法比",
                    fontSize = 12.sp,
                    color = TextPlaceholder,
                )
            }
        }
        return
    }
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
                        changePercent > 0 -> "↑ $changePercent%"
                        changePercent < 0 -> "↓ ${-changePercent}%"
                        else -> "持平"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
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
                            // 62dp：装得下 "15-30min" 这种 8 字符的档位标签
                            modifier = Modifier.width(62.dp),
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
                // 整行走基线对齐：中文日期与 `1h 30min` 的字体 ascent 不同，
                // 按中心对齐时数字会整体往下坠（他报的"最强的一天偏下"）
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
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
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    Text(
                        formatMinutesCompact(day.minutes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Primary,
                        modifier = Modifier.alignByBaseline(),
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

/** 分区错峰入场：淡入 + 上滑（强 ease-out：起步快、收尾稳） */
@Composable
private fun Entrance(
    played: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(Motion.DurationEnter, delayMillis = delayMillis, easing = Motion.EaseOut),
        label = "entranceAlpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (played) 0.dp else 24.dp,
        animationSpec = tween(Motion.DurationEnter, delayMillis = delayMillis, easing = Motion.EaseOut),
        label = "entranceOffset",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = offsetY.toPx() }) {
        content()
    }
}

private enum class StatValueKind { DURATION, MINUTES, DAYS, COUNT }

/**
 * 数值 → 文案。滚动过程中也走它，保证中间帧的格式和终值一致
 * （不然会看到 "1440min" 突然变 "24h"）。
 *
 * 时长类**全站统一走紧凑写法**（[formatMinutesCompact]）：三张卡并排 + 圆环中心 + 最强一天
 * 的右列都吃宽度，`1h 45min` 不会被挤到换行。
 */
private fun formatStatValue(kind: StatValueKind, value: Int): String = when (kind) {
    StatValueKind.DURATION, StatValueKind.MINUTES -> formatMinutesCompact(value)
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
    /** 主指标可以放大一档（三张等权大数字是模板味最重的地方，见调用处） */
    valueSize: TextUnit = 20.sp,
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
            // 值可能是 "1000h 30min"（10 字以上）或用户把系统字体调大：卡片只有 82dp 宽，
            // 不缩字号就会换行把卡片撑成两行、和旁边两张不齐（2026-10-26 实测 "30h 4min" 已占满 193/246px）。
            // autoSize 只在放不下时缩字，放得下时完全没变化。
            Text(
                text = text,
                fontSize = valueSize,
                fontWeight = FontWeight.Medium,
                color = Primary,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = valueSize,
                    stepSize = 1.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

/** 圆环图配色（按任务时长降序循环取色）；读取器形式，主题切换时取到当前色 */
private val DonutColors: List<Color>
    get() = listOf(Primary, AccentGreen, WarningAmber, Gold, PrimaryLight, SuccessGreen)

/** 任务分布卡片自己的时间档（与页面顶部的范围 chip 相互独立）；[ALL] 只在页面选「全部」时出现 */
private enum class TaskDistRange(val label: String) {
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
    ALL("全部"),
}

/**
 * 任务分布的数据源：按 [range] 取整段（今天 = 当天；本周 = 锚点所在整周；
 * 本月 = 锚点所在整月），再按任务聚合。
 *
 * 聚合口径与统计页其它地方**必须一致**：归属不到活任务的记录
 * （自由专注 / 任务已被彻底删）一律归「自由专注」，绝不回显已删任务名。
 */
private fun taskTotalsFor(
    records: List<FocusRecord>,
    tasks: List<FocusTask>,
    range: TaskDistRange,
    anchor: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<AppViewModel.TaskTotal> {
    val index = RecordLink.TaskIndex.of(tasks)
    // 全部档不分时间段，整段记录全算
    val from: LocalDate?
    val to: LocalDate?
    when (range) {
        TaskDistRange.ALL -> {
            from = null
            to = null
        }
        TaskDistRange.TODAY -> {
            from = anchor
            to = anchor
        }
        TaskDistRange.WEEK -> {
            from = anchor.with(DayOfWeek.MONDAY)
            to = from.plusDays(6)
        }
        TaskDistRange.MONTH -> {
            from = anchor.withDayOfMonth(1)
            to = from.plusMonths(1).minusDays(1)
        }
    }
    return records
        .filter { record ->
            if (from == null || to == null) return@filter true
            // 按结束时刻归日（全项目统一口径）
            val d = Instant.ofEpochMilli(record.endEpochMillis).atZone(zone).toLocalDate()
            !d.isBefore(from) && !d.isAfter(to)
        }
        .groupBy { index.ownerOf(it)?.id ?: RecordLink.FREE_NAME }
        .map { (key, list) ->
            AppViewModel.TaskTotal(
                name = index.byId[key]?.name ?: RecordLink.FREE_NAME,
                minutes = list.sumOf { it.minutes },
            )
        }
        .sortedByDescending { it.minutes }
}

/**
 * 左右翻动锚点：今天档 ±1 天 / 本周档 ±1 周 / 本月档 ±1 月。
 *
 * **不能翻到未来**（不晚于今天所在的日 / 周 / 月）：越界就原样返回，
 * 手势那边只看到"没变化 + 回弹动画"，不会出现空白的下个月。
 */
private fun shiftAnchor(
    anchor: LocalDate,
    range: TaskDistRange,
    direction: Int,
    today: LocalDate = LocalDate.now(),
): LocalDate {
    val moved = when (range) {
        TaskDistRange.ALL -> anchor
        TaskDistRange.TODAY -> anchor.plusDays(direction.toLong())
        TaskDistRange.WEEK -> anchor.plusWeeks(direction.toLong())
        TaskDistRange.MONTH -> anchor.plusMonths(direction.toLong())
    }
    val limit = when (range) {
        TaskDistRange.ALL -> today
        TaskDistRange.TODAY -> today
        TaskDistRange.WEEK -> today.with(DayOfWeek.MONDAY)
        TaskDistRange.MONTH -> today.withDayOfMonth(1)
    }
    val target = when (range) {
        TaskDistRange.ALL -> anchor
        TaskDistRange.TODAY -> moved
        TaskDistRange.WEEK -> moved.with(DayOfWeek.MONDAY)
        TaskDistRange.MONTH -> moved.withDayOfMonth(1)
    }
    return if (target.isAfter(limit)) anchor else moved
}

/** 卡片副标题：时间段 + 当前能怎么滑 */
private fun taskDistHint(
    range: TaskDistRange,
    anchor: LocalDate,
    today: LocalDate = LocalDate.now(),
): String {
    val base = taskDistPeriodLabel(range, anchor, today)
    return when (range) {
        TaskDistRange.ALL -> base
        TaskDistRange.TODAY -> "$base · 左右滑切换日期"
        TaskDistRange.WEEK -> "$base · 左右滑切换周"
        TaskDistRange.MONTH -> "$base · 左右滑切换月"
    }
}

/** 「今天 · 8月21日」/「上周 · 8月11日–8月17日」/「上月 · 7月」 */
private fun taskDistPeriodLabel(
    range: TaskDistRange,
    anchor: LocalDate,
    today: LocalDate = LocalDate.now(),
): String = when (range) {
    TaskDistRange.ALL -> "全部记录"

    TaskDistRange.TODAY -> when {
        anchor == today -> "今天 · ${anchor.monthValue}月${anchor.dayOfMonth}日"
        anchor == today.minusDays(1) -> "昨天 · ${anchor.monthValue}月${anchor.dayOfMonth}日"
        else -> "${anchor.monthValue}月${anchor.dayOfMonth}日"
    }

    TaskDistRange.WEEK -> {
        val monday = anchor.with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val prefix = when (monday) {
            thisMonday -> "本周"
            thisMonday.minusWeeks(1) -> "上周"
            else -> null
        }
        val span = "${monday.monthValue}月${monday.dayOfMonth}日–" +
            "${sunday.monthValue}月${sunday.dayOfMonth}日"
        if (prefix == null) span else "$prefix · $span"
    }

    TaskDistRange.MONTH -> {
        val ym = YearMonth.from(anchor)
        val current = YearMonth.from(today)
        val prefix = when (ym) {
            current -> "本月"
            current.minusMonths(1) -> "上月"
            else -> null
        }
        val text = if (ym.year == current.year) {
            "${ym.monthValue}月"
        } else {
            "${ym.year}年${ym.monthValue}月"
        }
        if (prefix == null) text else "$prefix · $text"
    }
}
