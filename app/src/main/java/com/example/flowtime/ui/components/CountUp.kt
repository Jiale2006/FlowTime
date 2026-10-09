package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

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

/**
 * 浮点版数量增长，给进度条这类 0..1 的比例用。
 *
 * ⚠️ 坑和 [rememberCountUp] 一模一样，而且更容易踩：
 * `animateFloatAsState(真实比例)` 的首帧起点就是那个比例，等于进度条一上来就长满了，
 * 完全看不到"生长"过程（今日目标的进度条就是这么废掉的）。
 * 所以凡是"要从 0 长出来"的动画，都必须先让目标停在 0，再改成真实值。
 */
@Composable
fun rememberCountUpFloat(
    value: Float,
    durationMillis: Int = 900,
    delayMillis: Int = 0,
): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "countUpFloat",
    )
    return animated
}

/**
 * 弹性版进度（0..1 的比例）：从 0 **弹**到 [value]，轻微冲过头再稳住（rubber-band 手感）。
 *
 * - 先让目标停在 0 再改真实值：与上面两个函数同理，否则首帧就是终值、没有动画
 * - spring 规格没有 delay 参数，所以 [delayMillis] 用协程 delay 实现
 * - 默认参数：dampingRatio 0.65 = 轻微过冲（"slight overshoot"），
 *   stiffness 1000 ≈ 半秒内收稳；想更弹就调低 damping，想更快就调高 stiffness
 *
 * ⚠️ 返回值可能**超过 1**（overshoot）——喂给 `fillMaxWidth(fraction)` 之前必须
 * `coerceIn(0f, 1f)`，否则 fraction 越界直接崩。要不要钳制、钳在哪，由调用方决定：
 * 钳在 1.0 的话"已达成"的进度条就没有回弹（视觉上无所谓，反正已经满了）。
 */
@Composable
fun rememberSpringProgress(
    value: Float,
    delayMillis: Int = 0,
    dampingRatio: Float = 0.65f,
    stiffness: Float = 1000f,
): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        target = value
    }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
        label = "springProgress",
    )
    return animated
}
