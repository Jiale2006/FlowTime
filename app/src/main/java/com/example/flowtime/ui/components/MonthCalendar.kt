package com.example.flowtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历（补录选日期用）：7 列网格，可左右滑翻月。
 *
 * 与 [MonthHeatmap] 的区别 —— 那个是**只读**的"坚持情况"展示，这个是**可选**的日期选择器：
 * 带选中态、今天标记、未来日期置灰。色阶复用 [heatColorOf]，两处观感一致。
 *
 * 翻月方向按日历惯例：**左滑看更晚、右滑看更早**（months 由早到晚排列，今天所在月是最后一页，
 * 所以左滑到当月就是尽头——未来不可补）。
 * 注意**不要**加 `reverseLayout = true`：那会把列表镜像，变成"左滑看更早"，与惯例相反。
 *
 * 格子背景淡染当天已有的专注时长，是为了让**空白的那几天自己跳出来**——
 * 补录要解决的就是"我哪天忘了记"，比让用户翻着数日子有用。
 *
 * [maxSelectable] 之外的日期不可点：补录不该补未来（未来日期落库会变成一条"还没发生"的记录）。
 */
@Composable
fun MonthCalendar(
    months: List<YearMonth>,
    minutesByDate: Map<LocalDate, Int>,
    selected: LocalDate,
    maxSelectable: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (months.isEmpty()) return
    // 打开时停在选中日期所在的那个月
    val initialPage = remember(months) {
        months.indexOf(YearMonth.from(selected)).takeIf { it >= 0 } ?: months.lastIndex
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { months.size }
    val current = months.getOrNull(pagerState.currentPage) ?: months.last()

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%d 年 %d 月".format(current.year, current.monthValue),
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (months.size > 1) {
                Text("右滑看更早的月份", fontSize = 11.sp, color = TextPlaceholder)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            MonthGrid(
                month = months[page],
                minutesByDate = minutesByDate,
                selected = selected,
                maxSelectable = maxSelectable,
                today = today,
                onSelect = onSelect,
            )
        }
    }
}

/** 一个月：周一起排，首尾补空位保持列宽 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    minutesByDate: Map<LocalDate, Int>,
    selected: LocalDate,
    maxSelectable: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    // 周一起排：1 号若是周三（weekday=3），前面空 2 格
    val blanks = firstDay.dayOfWeek.value - 1
    val dayCount = month.lengthOfMonth()
    val rows = (blanks + dayCount + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekLabels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - blanks
                    if (dayIndex !in 0 until dayCount) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = firstDay.plusDays(dayIndex.toLong())
                        DayCell(
                            date = date,
                            minutes = minutesByDate[date] ?: 0,
                            selected = date == selected,
                            enabled = !date.isAfter(maxSelectable),
                            isToday = date == today,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 单日格子：背景 = 已有专注的色阶；选中描主色边，今天描淡边，未来画不出东西 */
@Composable
private fun DayCell(
    date: LocalDate,
    minutes: Int,
    selected: Boolean,
    enabled: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (enabled) heatColorOf(minutes) else Color.Transparent)
            .then(
                when {
                    selected -> Modifier.border(1.5.dp, Primary, shape)
                    isToday -> Modifier.border(1.dp, Primary.copy(alpha = 0.45f), shape)
                    else -> Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (selected || isToday) FontWeight.Medium else FontWeight.Normal,
            color = when {
                !enabled -> TextPlaceholder.copy(alpha = 0.35f)
                selected -> Primary
                minutes > 0 -> TextPrimary
                else -> TextSecondary
            },
        )
    }
}
