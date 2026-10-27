package com.example.flowtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 任务详情的趋势条：每天一根、一条轴横滑到底（同统计页「全部」档那种连续滑动，不是翻页）。
 *
 * 窗口高度由调用方定死（任务详情 = 184dp），这里的分工：**表头 / 柱顶数字 / 日期行先占，柱区吃剩下的高度**，
 * 所以系统字体调大时柱区自己让位，日期行不会被裁掉（他 2026-10-27 反馈「后面都显示不全」）。
 * 归一化分母 = **全部天数里的最大一天**，最高那根恒撑满柱区，全部共享同一条基线。
 * 周一那根前留 12dp 空隙：上周周日和本周周一不再粘在一起。
 */
@Composable
fun TrendScrollStrip(
    days: List<AppViewModel.DayMinutes>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
) {
    if (days.isEmpty()) return
    val maxMinutes = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    // 停在最右（今天）：记录都往过去长，进页面先看到近期。key 带 days.size：记录变了要重新定位
    val listState = rememberLazyListState()
    LaunchedEffect(days.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        listState.scrollToItem(days.lastIndex)
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "合计 ${formatMinutesCompact(days.sumOf { it.minutes })}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "左右滑动看更早",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(days) { index, day ->
                TrendBar(
                    day = day,
                    // 归一化柱高（最高那天 = 1）→ 柱子在这条轴上可以互相比较
                    fill = day.minutes.toFloat() / maxMinutes,
                    barColor = barColor,
                    todayColor = todayColor,
                    // 周界：周一那根前多留一截。第一根不加（左边没东西粘）
                    modifier = Modifier.padding(start = if (day.isWeekStart && index > 0) 12.dp else 0.dp),
                )
            }
        }
    }
}

/** 趋势条上的一根：柱顶数字 + 柱身 + 日期，整体在柱区里底部对齐 */
@Composable
private fun TrendBar(
    day: AppViewModel.DayMinutes,
    fill: Float,
    barColor: Color,
    todayColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(30.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 柱区 = 窗口里去掉数字行与日期行剩下的高度；数字 + 柱身包在同一组里底部对齐，
        // 否则数字钉在槽顶、柱子缩在槽底，中间空一大片（2026-10-26 那轮踩过的坑）
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (day.minutes > 0) "${day.minutes}" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 4.dp)
                        .fillMaxHeight(fill)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.isToday) todayColor else barColor),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            day.label,
            fontSize = 10.sp,
            color = if (day.isToday) todayColor else TextSecondary,
            fontWeight = if (day.isToday) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 柱区总高：数字行 + 柱区 + 日期行定死，柱区吃剩下的高度（同任务详情 TrendScrollStrip）。
 *  卡片高度因此恒定，不会因为一天有记录、一天没记录而跳。 */
private val DailyStripHeight = 160.dp

/**
 * 按天柱状（统计页用）：本周 7 根 / 本月 28-31 根 / 全部上千根。
 * 超过 10 天时横向滚动（柱宽固定），否则撑满一行；日期每 5 天标一次（[labelEveryBar] 可改为每根都标，
 * 全部档这种稀疏数据必须每根都标，否则日期全被跳掉），今天用主色高亮。
 */
@Composable
fun DailyBarsStrip(
    days: List<AppViewModel.DayMinutes>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
    countDelayMillis: Int = 0,
    labelEveryBar: Boolean = false,
    /** false = 柱子直接画成最终高度（不做出场生长） */
    animate: Boolean = true,
) {
    if (days.isEmpty()) return

    // 分母 = **区间最大值**（同任务详情）：最高那根恒撑满柱区，其余按比例。
    // 以前是 max(最大值, 30)：区间只有 4min 时最高柱只占 13%，上面一大片空白像没渲染出来
    val maxMinutes = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    val scrollable = days.size > 10

    // 出场「逐根展开」只播一遍：播完后新滑进屏幕的柱子直接是成形高度 ——
    // 逐根 rememberGrowingFloat 在 LazyRow 里有坑：柱子被回收后滑回来会重长一次（闪一下）
    var entered by remember(days.size) { mutableStateOf(false) }
    if (animate) {
        // 最多错开 20 根（440ms）+ 一次进场时长，过了这个点就算播完
        LaunchedEffect(days.size) {
            delay(countDelayMillis + 80L + 20 * 22L + Motion.DurationChartEnter + 120L)
            entered = true
        }
    }

    if (!scrollable) {
        // 一周 7 根：撑满一行，逐根错开出场（间距小，错开才看得出顺序）
        Row(
            modifier = modifier.fillMaxWidth().height(DailyStripHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEachIndexed { index, day ->
                DailyBar(
                    day = day,
                    fill = rememberBarFill(
                        ratio = day.minutes.toFloat() / maxMinutes,
                        animate = animate,
                        entered = entered,
                        enterDelayMillis = countDelayMillis + 80 + index * 22,
                    ),
                    showLabel = true,
                    barColor = barColor,
                    todayColor = todayColor,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        return
    }

    // 可滚动的月份/全部视图默认停在月初，数据和"今天"都在最右边 → 布局完成后自动滚到今天。
    // key 带 days.size：切范围（本月↔全部）后要重新定位，否则会停在上一次的滚动位置
    val listState = rememberLazyListState()
    var autoScrolled by remember(days.size) { mutableStateOf(false) }
    LaunchedEffect(days.size) {
        // 等它真的量过一次，否则还不知道能滚多远
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        if (!autoScrolled) {
            listState.scrollToItem(days.lastIndex)
            autoScrolled = true
        }
    }
    // LazyRow 而不是 Row+horizontalScroll：全部档是「首条记录→今天」每天一根，
    // 现在 555 根、用几年就上千根；非懒加载时切到全部会在同一帧里全合成（实测最差单帧 0.6~0.8 秒）。
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().height(DailyStripHeight),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(days) { index, day ->
            DailyBar(
                day = day,
                // 横滑档从右（今天）往左逐根展开：屏幕就停在这一头，远处的柱子还在屏外，
                // 所以错开量只按「距今天几根」算（封顶 20 根），全部档不会攒出十几秒的延迟
                fill = rememberBarFill(
                    ratio = day.minutes.toFloat() / maxMinutes,
                    animate = animate,
                    entered = entered,
                    enterDelayMillis = countDelayMillis + 80 + (days.lastIndex - index).coerceAtMost(20) * 22,
                ),
                showLabel = labelEveryBar || index % 5 == 0 || day.isToday,
                barColor = barColor,
                todayColor = todayColor,
                // 30dp：装得下 "12/31" 这种 5 字符日期（26dp 会被裁成 "12/3"）
                modifier = Modifier.width(30.dp).fillMaxHeight(),
            )
        }
    }
}

/** 一根柱子：柱顶数字 + 柱身（同一组贴底对齐）+ 日期，[fill] 是归一化柱高（最高那根 = 1） */
@Composable
private fun rememberBarFill(
    ratio: Float,
    animate: Boolean,
    entered: Boolean,
    enterDelayMillis: Int,
): Float {
    // 始终调 rememberGrowingFloat（条件调用会让槽位错位）；播完后直接给最终高度，动画就成了 dead state
    val growing = rememberGrowingFloat(target = ratio, enterDelayMillis = enterDelayMillis)
    return if (animate && !entered) growing else ratio
}
@Composable
private fun DailyBar(
    day: AppViewModel.DayMinutes,
    fill: Float,
    showLabel: Boolean,
    barColor: Color,
    todayColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 柱区 = 去掉数字行与日期行剩余的高度；数字 + 柱身包在同一组里贴底，
        // 所以数字紧贴柱顶、所有柱子共享同一条基线（同任务详情 TrendScrollStrip）
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (day.minutes > 0) "${day.minutes}" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // fill 是归一化柱高（最高那根 = 1）→ 撑满柱区，0min 保留 4dp 小柱
                        .heightIn(min = 4.dp)
                        .fillMaxHeight(fill)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.isToday) todayColor else barColor),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (showLabel) day.label else "",
            fontSize = 10.sp,
            color = if (day.isToday) todayColor else TextSecondary,
            fontWeight = if (day.isToday) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

