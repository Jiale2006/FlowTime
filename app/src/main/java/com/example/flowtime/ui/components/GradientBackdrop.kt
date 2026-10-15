package com.example.flowtime.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.flowtime.ui.theme.GradCyan
import com.example.flowtime.ui.theme.GradFuchsia
import com.example.flowtime.ui.theme.GradNight
import com.example.flowtime.ui.theme.GradNightDeep
import com.example.flowtime.ui.theme.GradViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 现代渐变场景底（Modern Gradient 的"活力"来源）：
 * 深紫夜底（indigo-950 → 紫黑）铺底，叠三团巨型柔光球 ——
 * 紫（左上）/ 品红（右中）/ 青（左下），各自沿小半径圆周缓慢游动。
 *
 * 三个周期（20s / 26s / 23s）刻意不相等：周期相同会同步成"整屏一起脉动"，
 * 错开后才是各团独立漂移的呼吸感（发光体的相位错开是经典教训，见 Effects.kt）。
 * 位移幅度只 ±6% 屏宽：近距离长时间盯着才察觉，满足规范「克制/缓慢」的要求。
 *
 * 光球一律用径向渐变从实到全透明做**连续衰减**（不是多层硬边圆叠台阶），
 * 终止色用"同色 alpha=0"而非 Color.Transparent —— 后者非预乘插值会泛灰边。
 *
 * 动效走 rememberInfiniteTransition：系统的"移除动画"生效时它停在初值，
 * 天然满足 prefers-reduced-motion（无需额外判断）；只在本主题下被调用。
 */
@Composable
fun GradientBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gradientFlow")
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbViolet",
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbFuchsia",
    )
    val t3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbCyan",
    )

    Box(modifier.background(Brush.verticalGradient(listOf(GradNight, GradNightDeep)))) {
        Canvas(Modifier.fillMaxSize()) {
            fun orb(
                color: Color,
                baseX: Float,
                baseY: Float,
                radiusFraction: Float,
                alpha: Float,
                phase: Float,
            ) {
                val angle = phase * 2f * PI.toFloat()
                val cx = size.width * (baseX + 0.06f * cos(angle))
                val cy = size.height * (baseY + 0.06f * sin(angle))
                val radius = size.minDimension * radiusFraction
                val center = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alpha),
                            color.copy(alpha = alpha * 0.45f),
                            color.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            // 三团光球：紫（左上）/ 品红（右中）/ 青（左下）
            orb(GradViolet, 0.18f, 0.14f, 0.62f, 0.34f, t1)
            orb(GradFuchsia, 0.88f, 0.42f, 0.52f, 0.28f, t2)
            orb(GradCyan, 0.22f, 0.90f, 0.58f, 0.22f, t3)
        }
    }
}
