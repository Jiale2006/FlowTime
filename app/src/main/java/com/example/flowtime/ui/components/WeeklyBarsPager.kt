package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary

/**
 * 按周分页柱状图：index 0 = 本周，右滑翻页看更早的周（任务详情/统计/大类复用）。
 * reverseLayout 让"下一页（更早）"排在左侧，右滑手势即回拨时间。
 * 页首显示周标签 + 周合计 + 页码。
 */
@Composable
fun WeeklyBarsPager(
    pages: List<AppViewModel.WeekPage>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
    countDelayMillis: Int = 0,
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val current = pages.getOrNull(pagerState.currentPage) ?: pages.first()
    // 合计也滚动：翻页换周时数字自己数到新值，比直接跳字更容易看出"换了一周"
    val animatedTotal = rememberCountUp(current.totalMinutes, delayMillis = countDelayMillis)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "合计 ${formatMinutesHm(animatedTotal)} · ${pagerState.currentPage + 1}/${pages.size}",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            reverseLayout = true,
            modifier = Modifier.fillMaxWidth(),
        ) { pageIndex ->
            WeeklyBarRow(
                days = pages[pageIndex].days,
                barColor = barColor,
                todayColor = todayColor,
                countDelayMillis = countDelayMillis,
            )
        }
    }
}

/** 单周 7 天柱状图（分页/统计页复用）：柱形从 0 长起来，柱顶数字同步从 0 数上去 */
@Composable
fun WeeklyBarRow(
    days: List<AppViewModel.DayMinutes>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
    countDelayMillis: Int = 0,
) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }

    val maxMinutes = maxOf(days.maxOfOrNull { it.minutes } ?: 0, 30)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val grow by animateFloatAsState(
                targetValue = if (played) 1f else 0f,
                animationSpec = tween(520, delayMillis = countDelayMillis + 80 + index * 22, easing = FastOutSlowInEasing),
                label = "weekBarGrow$index",
            )
            val countUp = rememberCountUp(
                value = day.minutes,
                durationMillis = 520,
                delayMillis = countDelayMillis + index * 22,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (day.minutes > 0) "$countUp" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((4 + 106f * day.minutes / maxMinutes * grow).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.isToday) todayColor else barColor),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    day.label,
                    fontSize = 11.sp,
                    color = if (day.isToday) todayColor else TextSecondary,
                    fontWeight = if (day.isToday) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 按天柱状（统计页用）：本周 7 根 / 本月 28-31 根。
 * 超过 10 天时横向滚动（柱宽固定），否则撑满一行；日期每 5 天标一次，今天用主色高亮。
 */
@Composable
fun DailyBarsStrip(
    days: List<AppViewModel.DayMinutes>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
    countDelayMillis: Int = 0,
) {
    if (days.isEmpty()) return
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }

    val maxMinutes = maxOf(days.maxOfOrNull { it.minutes } ?: 0, 30)
    val scrollable = days.size > 10
    val scrollState = rememberScrollState()
    // 可滚动的月份视图默认停在月初，数据和"今天"都在最右边 → 布局完成后自动滚到今天
    var autoScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollable && !autoScrolled && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
            autoScrolled = true
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val grow by animateFloatAsState(
                targetValue = if (played) 1f else 0f,
                animationSpec = tween(520, delayMillis = countDelayMillis + 80 + index * 22, easing = FastOutSlowInEasing),
                label = "dayGrow$index",
            )
            val countUp = rememberCountUp(
                value = day.minutes,
                durationMillis = 520,
                delayMillis = countDelayMillis + index * 22,
            )
            Column(
                modifier = if (scrollable) Modifier.width(26.dp) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (day.minutes > 0) "$countUp" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((4 + 96f * day.minutes / maxMinutes * grow).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.isToday) todayColor else barColor),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (!scrollable || index % 5 == 0 || day.isToday) day.label else "",
                    fontSize = 10.sp,
                    color = if (day.isToday) todayColor else TextSecondary,
                    fontWeight = if (day.isToday) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
