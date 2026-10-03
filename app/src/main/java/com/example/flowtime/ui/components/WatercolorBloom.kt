package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.flowtime.ui.theme.WatercolorPeach
import com.example.flowtime.ui.theme.WatercolorRose
import com.example.flowtime.ui.theme.WatercolorSand
import com.example.flowtime.ui.theme.WatercolorTeal

/**
 * 水彩晕染底：多组 radial-gradient 色块叠加，模拟颜料在湿纸上向外扩散渗透。
 * 全部为低透明度叠加（水彩透明性），且静止不动 → 天然满足 reduced-motion。
 */
@Composable
fun WatercolorBloom(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        fun bloom(color: Color, fx: Float, fy: Float, radiusFraction: Float, alpha: Float) {
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

        // 边缘向外扩散的四团水彩（避开中心内容区，保证正文对比度）
        bloom(WatercolorPeach, 0.94f, 0.05f, 0.58f, 0.30f)
        bloom(WatercolorTeal, 0.04f, 0.26f, 0.52f, 0.26f)
        bloom(WatercolorRose, 0.84f, 0.90f, 0.50f, 0.18f)
        bloom(WatercolorSand, 0.08f, 0.94f, 0.44f, 0.20f)
    }
}
