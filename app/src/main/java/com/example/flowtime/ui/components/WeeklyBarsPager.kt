package com.example.flowtime.ui.components

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
                text = "合计 ${formatMinutesHm(current.totalMinutes)} · ${pagerState.currentPage + 1}/${pages.size}",
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

/**
 * 单周 7 天柱状图（分页/统计页复用）。只让柱子从 0 长起来，柱顶数字保持静态
 * （用户明确要求：趋势卡的数字不动）。
 */
@Composable
fun WeeklyBarRow(
    days: List<AppViewModel.DayMinutes>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentGreen.copy(alpha = 0.65f),
    todayColor: Color = Primary,
    countDelayMillis: Int = 0,
) {
    val maxMinutes = maxOf(days.maxOfOrNull { it.minutes } ?: 0, 30)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            // 🔴 动画对象是归一化柱高本身（不是出场倍率）：切周/切范围时柱子走位而不是瞬跳
            val fill = rememberGrowingFloat(
                target = day.minutes.toFloat() / maxMinutes,
                enterDelayMillis = countDelayMillis + 80 + index * 22,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (day.minutes > 0) "${day.minutes}" else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((4 + 106f * fill).dp)
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
) {
    if (days.isEmpty()) return

    val maxMinutes = maxOf(days.maxOfOrNull { it.minutes } ?: 0, 30)
    val scrollable = days.size > 10
    val scrollState = rememberScrollState()
    // 可滚动的月份/全部视图默认停在月初，数据和"今天"都在最右边 → 布局完成后自动滚到今天。
    // key 带 days.size：切范围（本月↔全部）后要重新定位，否则会停在上一次的滚动位置
    var autoScrolled by remember(days.size) { mutableStateOf(false) }
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
            // 同 [WeeklyBarRow]：动的是归一化柱高，数据变化才不会瞬跳
            val fill = rememberGrowingFloat(
                target = day.minutes.toFloat() / maxMinutes,
                enterDelayMillis = countDelayMillis + 80 + index * 22,
            )
            Column(
                // 30dp：装得下 "12/31" 这种 5 字符日期（26dp 会被裁成 "12/3"）
                modifier = if (scrollable) Modifier.width(30.dp) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                        .height((4 + 96f * fill).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (day.isToday) todayColor else barColor),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (!scrollable || labelEveryBar || index % 5 == 0 || day.isToday) day.label else "",
                    fontSize = 10.sp,
                    color = if (day.isToday) todayColor else TextSecondary,
                    fontWeight = if (day.isToday) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
