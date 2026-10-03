package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary

/** 圆环图切片：颜色 + 占比（0..1） */
data class DonutSlice(val color: Color, val fraction: Float)

/** 圆环统计图：粗弧分段 + 2° 间隔 + 中心文案，入场扫过动画 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerText: String,
    centerSubtext: String,
    modifier: Modifier = Modifier,
) {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val reveal by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "donutReveal",
    )

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.16f
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweepAngle = 360f * slice.fraction * reveal
                if (sweepAngle > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = (sweepAngle - 2f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                }
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 时长文案偏长（如「14 h 54 min」）时缩小字号，避免压到圆环上
            Text(
                text = centerText,
                fontSize = if (centerText.length > 8) 16.sp else 22.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(centerSubtext, fontSize = 11.sp, color = TextSecondary)
        }
    }
}
