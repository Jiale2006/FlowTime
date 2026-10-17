package com.example.flowtime.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.flowtime.ui.theme.Motion

/**
 * 带过冲的缓动曲线（业界常说的 easeOutBack）：先冲过目标约 10%，再回落稳住。
 * 用在"要从 0 长出来且想要一点弹性"的地方——比 spring 好控制：
 * **时长精确等于 durationMillis**，而 spring 的收尾由物理参数决定，没法和其他动画对齐。
 */
val OvershootEasing: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

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
    easing: Easing = FastOutSlowInEasing,
): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis, delayMillis = delayMillis, easing = easing),
        label = "countUpFloat",
    )
    return animated
}

/**
 * 柱/弧的"填充比例"：**动画对象是数值本身**，不是出场倍率。
 *
 * 为什么不用「倍率 × 数据」那套：倍率只在 `LaunchedEffect(Unit)` 里从 0 跑到 1 一次，
 * 之后切范围（今日/本周/本月）数据变了倍率不动 → 柱子整块**瞬跳**。
 * 这里第一次从 0 长到 [target]（带错峰延迟），之后每次 [target] 变化都从**当前值**
 * 重定向（`Animatable` 天然可打断），所以数值是"走"过去的而不是跳过去的。
 *
 * @param enterDelayMillis 只作用于首次入场；后续数据变化不再延迟（否则切范围要等错峰）
 */
@Composable
fun rememberGrowingFloat(
    target: Float,
    enterDelayMillis: Int = 0,
    enterDurationMillis: Int = Motion.DurationChartEnter,
    easing: Easing = Motion.EaseOut,
): Float {
    val anim = remember { Animatable(0f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        anim.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (entered) Motion.DurationDataChange else enterDurationMillis,
                delayMillis = if (entered) 0 else enterDelayMillis,
                easing = easing,
            ),
        )
        entered = true
    }
    return anim.value
}
