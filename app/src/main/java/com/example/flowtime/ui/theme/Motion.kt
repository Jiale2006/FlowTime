package com.example.flowtime.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

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

    /**
     * 翻牌页片：起手比 [EaseInOut] 更果断（前段少一点迟滞），
     * 落地仍保留长减速；不拿去影响全局长过渡。
     */
    val EaseFlip: Easing = CubicBezierEasing(0.35f, 0f, 0.16f, 1f)

    /** 按压反馈（除水彩外的主题）：手指下去必须立刻有反应 */
    const val DurationPress = 120

    /** 数据变化过渡：切范围 / 新记录时数值重定向，短促不抢戏 */
    const val DurationDataChange = 320

    /** 通用入场（淡入 + 轻微上滑） */
    const val DurationEnter = 420

    /** 图表首次从 0 长出来 */
    const val DurationChartEnter = 520

    /**
     * 底部导航指示器位移：阻尼 0.7（欠阻尼）→ 到位时约 4% 的轻微过冲再收住。
     * 用 spring 而不是 tween，胶囊"追"向新页签时才有重量感。
     */
    val SpringNavIndicator: SpringSpec<Dp> = spring(dampingRatio = 0.7f, stiffness = 420f)

    /** 选中图标：8% 的弹性放大；阻尼更低一点，按下时就该"弹一下" */
    val SpringNavIcon: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 650f)
}
