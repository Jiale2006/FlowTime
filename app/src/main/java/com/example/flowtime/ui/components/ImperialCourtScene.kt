package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.example.flowtime.ui.theme.ImperialGoldMist
import com.example.flowtime.ui.theme.ImperialInk
import com.example.flowtime.ui.theme.ImperialNight
import com.example.flowtime.ui.theme.ImperialPeacock

/**
 * 宫廷御制场景底（玄青金瓦）：「场景先于章法」——玄青渐变打底，
 * 叠孔雀青（右上）+ 金雾（左下）两枚大半径柔光斑，再加 2.5% 噪点颗粒。
 *
 * 面板是深漆实体卡，不透明但仍在承接场景微光 —— 所以这层光必须存在，
 * 否则整屏就是一块死黑（提示词明确禁平面纯色）。
 * 光斑全部避开中心内容区，保证正文对比度；整体静止 → 满足 reduced-motion。
 */
@Composable
fun ImperialCourtScene(modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(ImperialNight, ImperialInk)))) {
        Canvas(Modifier.fillMaxSize()) {
            fun well(color: Color, fx: Float, fy: Float, radiusFraction: Float, alpha: Float) {
                val center = Offset(size.width * fx, size.height * fy)
                val radius = size.minDimension * radiusFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alpha),
                            color.copy(alpha = alpha * 0.45f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            // 孔雀青（右上，如琉璃瓦上月色）+ 金雾（左下，暖）+ 中央极淡金雾
            // 垫在计时环后面，让环心有微光而不是死黑
            well(ImperialPeacock, 0.82f, 0.04f, 0.75f, 0.32f)
            well(ImperialGoldMist, 0.06f, 0.92f, 0.70f, 0.16f)
            well(ImperialGoldMist, 0.50f, 0.42f, 0.60f, 0.06f)
        }
        // 漆器颗粒：2.5% 噪点平铺，消除渐变的塑料感（与夜航共用同一枚画笔）
        Canvas(Modifier.fillMaxSize()) {
            drawContext.canvas.nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                NoisePaint,
            )
        }
    }
}
