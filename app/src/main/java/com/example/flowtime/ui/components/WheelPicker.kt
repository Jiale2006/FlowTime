package com.example.flowtime.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 滚轮单行高度：44dp = HIG 移动端最小触控目标（accessibility.md），一行一格 */
val WheelItemHeight: Dp = 44.dp

/** 滚轮可见行数（奇数：正中一行 = 选中项，上下各两行做滚动预览） */
const val WheelVisibleCount: Int = 5

/** 滚轮整体高度 */
val WheelHeight: Dp = WheelItemHeight * WheelVisibleCount

/**
 * 居中那一格的圆角底色（iOS 的选中带）。
 * 调用方把 [WheelColumn] 摆进同一个 [Box] 里即可，多列共用一条选中带。
 */
@Composable
fun WheelSelectionBand(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WheelItemHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(Primary.copy(alpha = 0.10f)),
    )
}

/**
 * 当前滚动视口里离中心最近的那一项下标；视口尚未布局（首帧）时返回 -1。
 * 早前这里在空视口时返回 0，LazyColumn 首次组合时 snapshotFlow 立刻吐出 0，
 * 把外部传进来的选中值（比如补录默认的此刻 14:23）悄悄改写成第 0 项——
 * 表现为弹窗刚打开就跳一下。返回 -1 表示"还读不到"，由调用方忽略。
 */
private fun LazyListState.centeredIndex(): Int {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return -1
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: -1
}

/**
 * 竖向滚轮列（iOS 风）：惯性滑动 + 自动吸附到整行，中心高亮带由 [WheelSelectionBand] 负责。
 * 只做「值」，配色/选中带交给调用方，这样一排滚轮能共用一条选中带。
 *
 * 顶部/底部渐隐遮罩由 [fadeColor] 控制（传所在容器的底色即可叠出淡出效果；
 * 传 null 则不加遮罩）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelItemHeight,
    visibleCount: Int = WheelVisibleCount,
    fadeColor: Color? = null,
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    // 上下各有 2 行的 contentPadding：滚轮视口的"内容起点"就是正中选中带，
    // 所以初始/滚动目标直接用 safeIndex（实测：LazyList 把目标项摆在 padding
    // 内沿=中心带；再减 2 会让默认值停在带子上方两格）
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val fling = rememberSnapFlingBehavior(listState, SnapPosition.Center)
    // 反馈回调用的始终是最新 lambda，避免把 onIndexChange 记进 snapshotFlow 的 key
    val latestOnChange by rememberUpdatedState(onIndexChange)
    val haptic = LocalHapticFeedback.current

    // 外部改了选中项（如切换日期时重置时刻）→ 平滑滚到位；用户正在滑时不抢方向盘
    LaunchedEffect(safeIndex) {
        if (!listState.isScrollInProgress) {
            if (listState.firstVisibleItemIndex != safeIndex || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(safeIndex, 0)
            }
        }
    }
    // 用户滑动 → 只回报落位的那一项；-1（视口未就绪）直接忽略。
    // 顺带在这里做触感反馈：只在"落位项真的变了"时震一下（跟系统时间选择器一致），
    // 不跟着每帧震——那会变成马达嗡嗡响。程序化滚动（上面的 LaunchedEffect）不震，
    // 因为它是外部改值的结果，不是用户在拨滚轮。
    //
    // primed 防抖：弹层首帧布局未稳时 centeredIndex 可能误读成 0，
    // 直接回写会把调用方的初值（如默认 45）吞成 0——必须等到
    // "读到初始项"或"用户真的开始滑"之后才开始回报。
    val initialSafeIndex = safeIndex
    LaunchedEffect(listState, items.size) {
        var lastHapticIndex = -1
        var primed = false
        snapshotFlow { Pair(listState.centeredIndex(), listState.isScrollInProgress) }
            .collect { (index, scrolling) ->
                if (index < 0) return@collect
                if (!primed) {
                    if (index == initialSafeIndex || scrolling) primed = true
                    return@collect
                }
                if (lastHapticIndex != -1 && index != lastHapticIndex && !scrolling) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastHapticIndex = index
                latestOnChange(index.coerceIn(0, items.lastIndex))
            }
    }

    // 上/下各留 (visibleCount-1)/2 行：滚动时中间那一行正好落在选中带上
    val centerPadding = itemHeight * ((visibleCount - 1) / 2)

    // 点选支持（iOS 滚轮同款）：点中某一行 → 滚到正中。
    // 做成逐行 clickable 而不是外层 detectTapGestures——后者会被 LazyColumn
    // 的 scrollable 吃掉点击，实测点选完全无响应。
    val scope = rememberCoroutineScope()
    Box(
        modifier.height(itemHeight * visibleCount),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = centerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = fling,
        ) {
            itemsIndexed(items, key = { _, index -> index }) { index, text ->
                val selected = index == safeIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (index != safeIndex) scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        fontSize = 20.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) TextPrimary else TextPrimary.copy(alpha = 0.32f),
                    )
                }
            }
        }
        // 上下淡出遮罩：选中带之外的内容"化"进容器底色，市面滚轮都有的层次感
        if (fadeColor != null) {
            val fadeHeight = itemHeight
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(
                        Brush.verticalGradient(listOf(fadeColor, Color.Transparent)),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, fadeColor)),
                    ),
            )
        }
    }
}

/** 一天的总分钟数：时/分两列不设人为截断时的兜底上限 */
private const val MINUTES_PER_DAY = 24 * 60

/**
 * 一行「小时 + 分钟」滚轮：外圈画选中带与淡出遮罩，内部两列共用。
 *
 * **组件不做任何上限钳制**：小时列 0..maxHours、分钟列固定 0..59，随便拨。
 *
 * 为什么不做（两次都栽在这上面）：
 * - 第一版让两列互相当对方的"上限"（`hourMax` 依赖分钟、`minuteMax` 依赖小时）：
 *   分钟停在 30 时拨小时到 8，小时列会从 0..8 缩成 0..7，**刚拨上去的 8 被静默顶回 7**；
 * - 第二版改成"拨完把越界的那一列压到合法值"：于是上限落在整点时（24 小时），
 *   **分钟列拨到 20 或 40 都会自己弹回 0**——用户的原话是"分钟不管拨到几都自动到 0"。
 *
 * 两种做法的共同毛病都是**让用户看到"拨了没反应"**，那比"允许选出一个越界值、
 * 再在确定时兜住并写明按什么记录"糟糕得多。
 * 所以越界处理统一交给调用方在**确定 / 使用时**做（见 `WheelPickerDialog` 的 clampTo）。
 */
@Composable
fun HourMinuteWheel(
    hours: Int,
    minutes: Int,
    onChange: (hours: Int, minutes: Int) -> Unit,
    maxTotalMinutes: Int = MINUTES_PER_DAY,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 72.dp,
    fadeColor: Color? = null,
) {
    val maxHours = (maxTotalMinutes / 60).coerceIn(0, 24)
    val h = hours.coerceIn(0, maxHours)
    val m = minutes.coerceIn(0, 59)
    val hourItems = remember(maxHours) { (0..maxHours).map { "%02d".format(it) } }
    val minuteItems = remember { (0..59).map { "%02d".format(it) } }

    Box(modifier.fillMaxWidth().height(WheelHeight), contentAlignment = Alignment.Center) {
        WheelSelectionBand()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                items = hourItems,
                selectedIndex = h,
                onIndexChange = { picked -> onChange(picked, m) },
                modifier = Modifier.width(columnWidth),
                fadeColor = fadeColor,
            )
            Spacer(Modifier.width(6.dp))
            Text("小时", fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.9f))
            Spacer(Modifier.width(18.dp))
            WheelColumn(
                items = minuteItems,
                selectedIndex = m,
                onIndexChange = { picked -> onChange(h, picked) },
                modifier = Modifier.width(columnWidth),
                fadeColor = fadeColor,
            )
            Spacer(Modifier.width(6.dp))
            Text("分钟", fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.9f))
        }
    }
}

/**
 * 时长滚轮（补录选时长、改时长共用）。
 *
 * 2026-10-09 定稿：**不再带快选 chip**——补录的主场景是"补昨天那一整段"，
 * 默认值由 `BackfillRules` 猜，多数时候不用碰滚轮；chip 反倒占掉本可以用来
 * 压缩弹层高度的空间。滚轮本身覆盖 0–8h 任意值。
 */
@Composable
fun DurationWheelBlock(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxTotalMinutes: Int = MINUTES_PER_DAY,
    columnWidth: Dp = 72.dp,
    fadeColor: Color? = null,
) {
    val safeMinutes = minutes.coerceIn(0, maxTotalMinutes)
    HourMinuteWheel(
        hours = safeMinutes / 60,
        minutes = safeMinutes % 60,
        onChange = { h, m -> onChange((h * 60 + m).coerceIn(0, maxTotalMinutes)) },
        maxTotalMinutes = maxTotalMinutes,
        modifier = modifier,
        columnWidth = columnWidth,
        fadeColor = fadeColor,
    )
}

private val WeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private val ActiveChipShape = RoundedCornerShape(9.dp)

/** 过去某天的短标签：今天 / 昨天 / 前天 / 10-06 周二（daysAgo = 距今天数） */
fun pastDayLabel(today: LocalDate, daysAgo: Int): String = pastDayLabelOf(
    today.minusDays(daysAgo.toLong()),
    today,
)

private fun pastDayLabelOf(date: LocalDate, today: LocalDate): String = when (today.toEpochDay() - date.toEpochDay()) {
    0L -> "今天"
    1L -> "昨天"
    2L -> "前天"
    else -> "%02d-%02d %s".format(
        date.monthValue,
        date.dayOfMonth,
        WeekdayLabels[date.dayOfWeek.value - 1],
    )
}

/** 单个日期快选 chip（今天 / 昨天 / 前天 / 更多） */
@Composable
fun DayQuickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(ActiveChipShape)
            .background(if (selected) CardGreenLight else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Primary else TextSecondary,
        )
    }
}
