package com.example.flowtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * 环形倒计时（教程 §3.1）：
 * 底色弧 + 绿→青渐变进度弧，圆角线帽，弧首圆点，中央大号时间。
 */
@Composable
fun RingTimer(
    progress: Float,
    timeText: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    timeColor: Color = TextPrimary,
    /** 运行中：环外围柔光呼吸脉动 */
    breathing: Boolean = false,
    /** 暂停态：整体降透明度，形成明确状态区分 */
    dimmed: Boolean = false,
    /** 秒级涟漪：当前秒内进度 0..1，null 不绘制 */
    rippleFraction: Float? = null,
    centerContent: (@Composable () -> Unit)? = null,
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)
    val ringAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.55f else 1f,
        animationSpec = tween(300),
        label = "ringDim",
    )

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        if (breathing) {
            RingGlow(strokePx = strokePx)
        }
        // 不再用 graphicsLayer 承载整体透明度：整块图层在页面切换时容易留下残影。
        // 改成把 ringAlpha 直接乘进每次绘制的颜色里 —— 视觉一致，但不需要图层。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringA = ringAlpha
            val inset = strokePx / 2 + strokePx * 0.9f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            // 秒级涟漪：每秒从环缘向外扩散一圈细波
            rippleFraction?.let { fraction ->
                drawCircle(
                    color = AccentGreen.copy(alpha = (1f - fraction) * 0.22f * ringA),
                    radius = radius + strokePx * (0.8f + 2.4f * fraction),
                    center = center,
                    style = Stroke(width = strokePx * 0.14f),
                )
            }

            // 背景装饰点（示例图环形周围的散点）
            val dotPositions = listOf(
                0.12f to 0.08f, 0.86f to 0.12f, 0.95f to 0.42f, 0.90f to 0.78f,
                0.08f to 0.34f, 0.05f to 0.72f, 0.30f to 0.94f, 0.70f to 0.95f,
            )
            dotPositions.forEachIndexed { index, (fx, fy) ->
                drawCircle(
                    color = if (index % 2 == 0) {
                        AccentGreen.copy(alpha = 0.35f * ringA)
                    } else {
                        CardGreenLight.copy(alpha = ringA)
                    },
                    radius = strokePx * (if (index % 3 == 0) 0.30f else 0.18f),
                    center = Offset(size.width * fx, size.height * fy),
                )
            }

            // 刻度线（12 条，弱化）
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) - 90.0)
                val outer = radius + strokePx * 0.9f
                val inner = outer - strokePx * 0.55f
                drawLine(
                    color = AccentGreen.copy(alpha = 0.4f * ringA),
                    start = Offset(
                        center.x + (inner * cos(angle)).toFloat(),
                        center.y + (inner * sin(angle)).toFloat(),
                    ),
                    end = Offset(
                        center.x + (outer * cos(angle)).toFloat(),
                        center.y + (outer * sin(angle)).toFloat(),
                    ),
                    strokeWidth = strokePx * 0.10f,
                    cap = StrokeCap.Round,
                )
            }

            // 底色弧
            drawArc(
                color = CardGreenLight.copy(alpha = 0.7f * ringA),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // 渐变进度弧（从顶部顺时针）：半透明叠色，模拟水彩在纸上的渗透
            if (clamped > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        // 水彩渗透：青绿 → 蓝灰 → 青绿，低饱和叠色过渡（不掺沙色，避免发浑）
                        colors = listOf(AccentGreen, PrimaryLight, Primary, PrimaryLight, AccentGreen),
                        center = center,
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = 0.78f * ringA,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                // 弧首圆点（缩小并降透明度，避免生硬）
                val headAngle = Math.toRadians((-90f + 360f * clamped).toDouble())
                drawCircle(
                    color = Primary.copy(alpha = 0.7f * ringA),
                    radius = strokePx * 0.52f,
                    center = Offset(
                        center.x + (radius * cos(headAngle)).toFloat(),
                        center.y + (radius * sin(headAngle)).toFloat(),
                    ),
                )
                // 弧首彗尾：沿弧向后的渐隐拖尾，制造运动质感
                if (clamped > 0.02f) {
                    for (k in 1..8) {
                        val trailAngle = headAngle - k * 0.05
                        drawCircle(
                            color = Primary.copy(alpha = (1f - k / 9f) * 0.28f * ringA),
                            radius = strokePx * (0.5f - k * 0.04f),
                            center = Offset(
                                center.x + (radius * cos(trailAngle)).toFloat(),
                                center.y + (radius * sin(trailAngle)).toFloat(),
                            ),
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = timeText,
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "timeTick",
            ) { text ->
                Text(
                    text = text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    color = timeColor,
                )
            }
            centerContent?.invoke()
        }
    }
}

/** 呼吸光环：环外围两层柔光随 6s 周期脉动（比正常呼吸慢一拍，进入减速曲线） */
@Composable
private fun RingGlow(strokePx: Float) {
    val transition = rememberInfiniteTransition(label = "ringGlow")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inset = strokePx / 2 + strokePx * 0.9f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = AccentGreen.copy(alpha = 0.10f + 0.10f * breath),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx * (2.0f + 0.5f * breath), cap = StrokeCap.Round),
        )
        drawArc(
            color = AccentGreen.copy(alpha = 0.05f + 0.07f * breath),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx * (3.2f + 0.8f * breath), cap = StrokeCap.Round),
        )
    }
}
