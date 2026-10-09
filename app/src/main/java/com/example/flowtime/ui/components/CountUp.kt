package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 数字滚动：进入页面时从 0 数到 [value]；[value] 变化时（切范围 / 新记录）平滑改到新值。
 *
 * 为什么先让目标停在 0、再由 LaunchedEffect 改成真实值：
 * 直接 `animateIntAsState(value)` 的话，第一帧的动画起点就已经是 value，
 * 等于没有滚动过程（数字一上来就是终值）。必须先有一帧目标是 0，
 * 才有"从 0 数上去"的观感。
 *
 * [delayMillis] 用来对齐所在分区的入场延迟：统计页各卡是错峰滑入的，
 * 数字若统一从 t=0 开始数，靠下的卡片会"数字先数完、卡片才出现"。
 */
@Composable
fun rememberCountUp(
    value: Int,
    durationMillis: Int = 800,
    delayMillis: Int = 0,
): Int {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "countUp",
    )
    return animated
}
