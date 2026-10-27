package com.example.flowtime.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻牌钟几何的回归测试。
 *
 * 为什么值得写：2026-10-25 之前"卡片压环"是**肉眼报出来的** —— 4 位时行宽超过
 * 环径（行/径 1.06），5 位（≥1 小时）时四角切进描边 6dp，都是靠截图量像素才发现。
 * 这类问题的共同点是"参数一改就回来"，而它只跟几条 dp 数字有关、跟界面无关，
 * 正是单测最划算的地方：以后谁再动 `fitInset` / `cardHeight` / 冒号宽度，
 * 这里会先红。
 */
class FlipClockFitTest {

    private val aspect = 0.82f
    private val colonWidth = 12f
    private val gap = 6f

    /**
     * 判据一：解出来的卡高必须**正好内切** —— 四角落在圆周上（误差 < 0.05 单位）。
     *
     * 这条守的是公式本身。如果哪天有人把二次方程抄错、或者忘了某个间隔，
     * 卡高会偏大/偏小，四角就不再落在圆上。
     */
    @Test
    fun fittedCardHeightIsTangentToCircle() {
        val radii = listOf(60f, 100f, 125.8f, 160f, 200f)
        for (slots in 4..6) {
            for (colonCount in 1..2) {
                for (r in radii) {
                    val h = FlipClockFit.maxCardHeightInCircle(
                        r, slots, colonCount, aspect, colonWidth, gap,
                    )
                    assertTrue("slots=$slots colon=$colonCount r=$r 应解出正卡高", h > 0f)
                    val corner = FlipClockFit.cornerRadius(
                        h, slots, colonCount, aspect, colonWidth, gap,
                    )
                    assertEquals(
                        "slots=$slots colon=$colonCount r=$r 四角应落在圆周上",
                        r.toDouble(),
                        corner.toDouble(),
                        0.05,
                    )
                }
            }
        }
    }

    /**
     * 判据二：**真实布局**下必须留出 10dp 空气。
     *
     * 数字来自生产代码（440dp 设计宽，见 AdaptiveScale）：
     * - 环方块 = 0.92 × 440 = 405dp
     * - 环内缘 = 405/2 − (边距 8.4 + 半个描边 3) = 191.1dp
     * - 卡片四角与内缘必须留 `fitClearance` = 10dp
     *
     * 4 位（<1 小时）与 5 位（≥1 小时）都要过 —— 后者是老 bug 的现场：
     * 旧实现只扣固定 12dp 左右边距，5 位时行宽 365dp ≈ 环径 382dp 的 0.96，
     * 四角压进描边。
     */
    @Test
    fun realLayoutKeepsTenDpAir() {
        val maxWidth = 0.92f * 440f      // RingTimer 方块边长
        val fitInset = 8.4f + 3f         // 环边距 + 半个描边
        val clearance = 10f
        val cap = 84f                    // FlipClock 的 cardHeight 上限
        val inner = maxWidth / 2f - fitInset

        for (slots in 4..6) {
            val colonCount = if (slots >= 5) 2 else 1
            val byCircle = FlipClockFit.maxCardHeightInCircle(
                inner - clearance, slots, colonCount, aspect, colonWidth, gap,
            )
            val fitted = minOf(byCircle, cap)
            val corner = FlipClockFit.cornerRadius(
                fitted, slots, colonCount, aspect, colonWidth, gap,
            )
            assertTrue(
                "slots=$slots fitted=$fitted corner=$corner inner=$inner 应留出 ≥${clearance}dp",
                corner + clearance <= inner + 0.01f,
            )
        }
    }

    /**
     * 判据三：卡数越多，卡高必须越小（否则就是约束写反了）。
     *
     * 这条守的是"跨小时重排"：从 4 位变 5 位时，卡高只能单调下降，
     * 不能出现"多一张卡反而更大"这种反直觉跳变。
     */
    @Test
    fun moreSlotsMeansSmallerCards() {
        val r = 125.8f
        val heights = (4..6).map { slots ->
            val colonCount = if (slots >= 5) 2 else 1
            FlipClockFit.maxCardHeightInCircle(r, slots, colonCount, aspect, colonWidth, gap)
        }
        for (i in 1 until heights.size) {
            assertTrue(
                "卡数增加时卡高必须下降：${heights[i - 1]} → ${heights[i]}",
                heights[i] < heights[i - 1],
            )
        }
    }

    /**
     * 判据四：宽高比/冒号宽度的自检 —— 圆心可用半宽等于"固定宽度的一半"时，
     * 判别式为 0，函数必须返回 0 而不是 NaN（除零/负数开方）。
     */
    @Test
    fun degenerateCircleReturnsZeroInsteadOfNaN() {
        // 圆只比冒号+间隔略宽：slots=4, 固定宽 = 12 + 4*6 = 36，半宽 18 时恒不可行
        val h = FlipClockFit.maxCardHeightInCircle(18f, 4, 1, aspect, colonWidth, gap)
        assertTrue("退化为 0 而不是 NaN：h=$h", h == 0f)
        assertTrue("宽为负也要安全", FlipClockFit.maxCardHeightInCircle(-5f, 4, 1, aspect, colonWidth, gap) == 0f)
    }
}
