package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 柔和按压：按下轻微内收至 0.98，抬起回到原位。
 *
 * 时长分主题（2026-10-17 动效审计）：
 * - 水彩：沿用规范里的「液态慢收」400ms —— 风格提示词明文禁止快速缓动，别动。
 * - 其余主题：120ms 强 ease-out。按压反馈必须跟手：400ms 的 FastOutSlowIn 前 100ms
 *   只走 17%，scale 才变 0.34%，肉眼等同"按了没反应"。
 *
 * @param onPressChange 按下/抬起回调 —— 玻璃拟态拿它驱动环形高光的受光响应（Liquid Glass 的交互受光）
 */
fun Modifier.pressScale(onPressChange: (Boolean) -> Unit = {}): Modifier = composed {
    val slowLiquid = ActivePalette.mode == ThemeMode.WATERCOLOR
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = if (slowLiquid) {
            tween(durationMillis = 400, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = Motion.DurationPress, easing = Motion.EaseOut)
        },
        label = "pressScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                onPressChange(true)
                waitForUpOrCancellation()
                pressed = false
                onPressChange(false)
            }
        }
}
