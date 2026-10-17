package com.example.flowtime.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.BgPrimary
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextSecondary
import java.time.LocalDate

internal val WeekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 色阶图例（由浅到深，与 [heatColorOf] 一一对应）；读取器形式，主题切换时取到当前色。
 *
 * 🔴 直接复用 [ActivePalette.heatRamp] —— 这里原来**又抄了一份**同样的五档色，
 *    深底主题改成不透明色阶后两份必然对不上（图例一个紫、格子另一个紫）。
 */
private val HeatLegend: List<Color>
    get() = ActivePalette.heatRamp

/**
 * 一个月热力图：7 列（一~日）× 若干行圆角格子，颜色深浅代表当日专注时长。
 *
 * [monthStart] 决定首行前面留几个空格（周一起排）；
 * 范围外的日子淡显（聚焦当前所选范围），未来日期落回纸色；整块淡入入场。
 */
@Composable
fun MonthHeatmap(
    monthStart: LocalDate,
    cells: List<AppViewModel.HeatCell>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val reveal by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(Motion.DurationChartEnter, easing = Motion.EaseOut),
        label = "heatReveal",
    )

    // 周一起排：1 号若是周三（weekday=3），前面空 2 格
    val blanks = monthStart.dayOfWeek.value - 1
    val slots: List<AppViewModel.HeatCell?> = List(blanks) { null } + cells

    Column(modifier.fillMaxWidth()) {
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
        slots.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { cell ->
                    if (cell == null) {
                        // 首尾的占位格：保持列宽一致，不画东西
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        // 切换月份时格子颜色按数据 tween 过去（原来直接换色 = 整片瞬跳）
                        val fill by animateColorAsState(
                            targetValue = heatColor(cell),
                            animationSpec = tween(Motion.DurationDataChange, easing = Motion.EaseOut),
                            label = "heatCellColor",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                // 入场动画必须作用在颜色 alpha 上：Modifier.alpha() 放在 .background() 之后
                                // 会把整块（含背景）画成不可见，实测热力图会整片空白。
                                .background(fill.copy(alpha = (fill.alpha * reveal).coerceIn(0f, 1f))),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 格子里写日号：只有色块时，根本看不出哪一格是几号
                            Text(
                                text = cell.date.dayOfMonth.toString(),
                                fontSize = 11.sp,
                                color = inkOn(fill).copy(alpha = reveal),
                            )
                        }
                    }
                }
                // 最后一行不足 7 格时补空位，避免格子被拉宽
                repeat(7 - week.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("少", fontSize = 10.sp, color = TextPlaceholder)
            HeatLegend.forEach { color ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Text("多", fontSize = 10.sp, color = TextPlaceholder)
        }
    }
}

/**
 * 可左右滑动的月份热力图：一页一个月，右滑看更早的月份。
 * 表头的"当月统计"跟着当前页走，所以直接放在每一页里画。
 *
 * 翻月方向按日历惯例（右滑看更早、左滑看更晚）。**别加 `reverseLayout = true`**：
 * 那会把列表镜像成"左滑看更早"，正好和下面那句提示、以及所有日历 App 的手感相反。
 */
@Composable
fun MonthHeatmapPager(
    months: List<AppViewModel.HeatMonth>,
    modifier: Modifier = Modifier,
) {
    if (months.isEmpty()) return
    // 默认停在当月（列表由早到晚排，最后一页就是当月）
    val pagerState = rememberPagerState(initialPage = months.lastIndex) { months.size }
    val current = months.getOrNull(pagerState.currentPage) ?: months.last()

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                current.label,
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${current.activeDays} 天有记录 · 共 ${formatMinutesHm(current.totalMinutes)}",
                fontSize = 11.sp,
                color = TextPlaceholder,
            )
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            MonthHeatmap(
                monthStart = months[page].firstDay,
                cells = months[page].cells,
            )
        }
        if (months.size > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                "右滑看更早的月份 · ${pagerState.currentPage + 1}/${months.size}",
                fontSize = 11.sp,
                color = TextPlaceholder,
            )
        }
    }
}

/** 单格颜色：5 档半透明水彩色阶；范围外淡显 */
private fun heatColor(cell: AppViewModel.HeatCell): Color {
    val base = heatColorOf(cell.minutes)
    // 范围外整体轻微淡显：既聚焦当前范围，又保留坚持情况的可读性
    return if (cell.inRange) base else base.copy(alpha = base.alpha * 0.7f)
}

/**
 * 专注分钟数 → 主题色阶（5 档）。
 * 刻意**只依赖分钟数**，好让补录日历（[MonthCalendar]）复用同一套观感。
 *
 * 取色统一走 [ActivePalette.heatRamp]：纸底主题是半透明颜料叠色，深底主题是
 * **不透明**色阶（深底直接用半透明色会读不出趋势，见该属性的说明）。
 */
fun heatColorOf(minutes: Int): Color {
    val idx = when {
        minutes <= 0 -> 0
        minutes < 30 -> 1
        minutes < 60 -> 2
        minutes < 90 -> 3
        else -> 4
    }
    return ActivePalette.heatRamp.getOrElse(idx) { ActivePalette.cardCream }
}

/** source-over 叠加；要求 [bg] 已不透明，结果强制不透明 */
private fun over(fg: Color, bg: Color): Color = Color(
    red = fg.red * fg.alpha + bg.red * (1f - fg.alpha),
    green = fg.green * fg.alpha + bg.green * (1f - fg.alpha),
    blue = fg.blue * fg.alpha + bg.blue * (1f - fg.alpha),
    alpha = 1f,
)

/** 格子上的字：纯黑 / 纯白两个极端，中间色在深浅两头的底色上都会读不清 */
private val DarkInk = Color(0xFF000000)

/**
 * 两个候选字色的**对比度相等的那个点**：
 * 黑字 = 20·L + 1，白字 = 1.05 / (L + 0.05)，令两者相等解出 L ≈ 0.18。
 * 阈值取在这里，无论格子深浅，选中的字色都不会掉到 4.5:1 以下。
 */
private const val INK_FLIP_LUMINANCE = 0.18f

/** 色块叠在卡片、卡片叠在页面底之后，格子的**最终可见色**（对比度测试也用它） */
internal fun cellSurface(
    fill: Color,
    cardSurface: Color = SurfaceCard,
    pageBackground: Color = BgPrimary,
): Color = over(fill, over(cardSurface, pageBackground))

/**
 * 色块上的字色：按**最终叠出来的实际深浅**决定，不写死主题的 textPrimary。
 *
 * 必须把底色传进来算，原因有两层：
 * 1. 三套主题的卡片底色**明暗相反**——纸主题是米白、夜航是 94% 不透明的深钢蓝；
 * 2. 色阶本身是**半透明**的（玻璃主题的 cardCream 甚至只有 3% 白）。
 *
 * 踩过的两个坑，都被 `HeatContrastTest` 钉住了：
 * - 直接拿 cardCream 当底色算 → 夜航下算成"纯白底" → 选深墨字，糊在深色卡上；
 * - 候选字色用 0x2B2B2B 这种"近黑"，在水彩最深档（蓝 75%）上只有 4.19:1，不到 WCAG 的 4.5。
 *   换成纯黑/纯白并把阈值放到两候选相等点，才全档达标。
 */
internal fun inkOn(
    fill: Color,
    cardSurface: Color = SurfaceCard,
    pageBackground: Color = BgPrimary,
): Color =
    if (cellSurface(fill, cardSurface, pageBackground).luminance() > INK_FLIP_LUMINANCE) {
        DarkInk
    } else {
        Color.White
    }
