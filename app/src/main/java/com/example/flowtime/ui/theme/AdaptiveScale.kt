package com.example.flowtime.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** 设计基准宽度：屏幕宽 ≥ 此值时按原始设计 1:1 显示（雷电模拟器 443dp、大屏手机都在这一档） */
private const val DESIGN_WIDTH_DP = 440f

/**
 * 缩放下限：0.81 对应约 356dp 宽的屏幕。
 * 参照机型 —— 小米 Redmi（最小宽度 361dp）：361/440 = 0.82，
 * 此时 52dp 主按钮 → 43dp、48dp 次按钮 → 39dp，落在 Material 3 标准按钮高度（40dp）附近，
 * 再往下就会明显难点准。
 */
private const val MIN_SCALE = 0.81f

/**
 * 文字缩放的独立下限（最多收 6%）。
 * 时间类 App 里 dp 靠缩放收得动，但字号不能跟着等比收：
 * 说明文字本来就只有 12~13sp，收到 0.82 会掉到 10sp 以下、明显难读。
 */
private const val MIN_FONT_SCALE = 0.94f

/**
 * 按屏幕宽度等比缩放整套尺寸（dp + sp）。
 *
 * 为什么需要：dp 是"密度无关"，但**不是"尺寸无关"**。
 * 同一个 48dp 高的按钮，在 443dp 宽的屏幕上占屏宽 11%，在 360dp 宽的手机上占 13%；
 * 标题、卡片内边距、圆角同理。窄屏上它们一起偏大，观感就是"什么都大一号"。
 *
 * 做法：density 与 fontScale 同时乘以 (屏宽 / 440)，clamp 到 [0.85, 1]：
 * - 443dp（雷电模拟器）：1.00，完全不变；
 * - 411dp（常见手机宽度）：约 0.93；
 * - 361dp（本机 Redmi）：0.82；
 * - 文字缩放比图形更保守（下限 0.94），且用户自己放大过系统字体时不再叠加缩小。
 *
 * 因为是等比缩放，元素的**相对比例关系保持不变**——等于把整个设计稿等比缩了一遍，
 * 所以窄屏上的观感会和大屏上一致，而不是"某个元素单独变小"。
 */
@Composable
fun AdaptiveScale(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val base = LocalDensity.current
    val scale = (widthDp / DESIGN_WIDTH_DP).coerceIn(MIN_SCALE, 1f)
    // 大屏不缩放；系统字体已被用户放大时不叠加缩小
    if (scale >= 1f || base.fontScale > 1.3f) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = base.density * scale,
            fontScale = base.fontScale * scale.coerceAtLeast(MIN_FONT_SCALE),
        ),
    ) {
        content()
    }
}
