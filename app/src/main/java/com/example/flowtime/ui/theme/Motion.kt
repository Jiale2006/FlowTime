package com.example.flowtime.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 全站动效 token —— 唯一来源，别再散落写 `tween(700)` / `FastOutSlowInEasing`。
 *
 * 取值依据（Emil Kowalski 动效口径，2026-10-17 审计）：
 * - 入场 / 出现 → 强 ease-out（起步快、收尾稳）；`FastOutSlowInEasing` 起步慢，入场会"发拖"。
 * - 屏幕上位移 / 变形 → 强 ease-in-out。
 * - UI 动效预算 300ms 内；图表这类"解释性"可放宽到 ~520ms。
 * - 数据变化的过渡要短（[DurationDataChange]）：它替代的是"瞬跳"，不是"表演"。
 */
object Motion {

    /** 强 ease-out：入场、出现、数据变化的默认曲线 */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 强 ease-in-out：界面内位移 / 形态变化 */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** 按压反馈（除水彩外的主题）：手指下去必须立刻有反应 */
    const val DurationPress = 120

    /** 数据变化过渡：切范围 / 新记录时数值重定向，短促不抢戏 */
    const val DurationDataChange = 320

    /** 通用入场（淡入 + 轻微上滑） */
    const val DurationEnter = 420

    /** 图表首次从 0 长出来 */
    const val DurationChartEnter = 520
}
