package com.example.flowtime.ui.components

import android.graphics.Bitmap
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
import com.example.flowtime.ui.theme.Champagne
import com.example.flowtime.ui.theme.MoonSteel
import com.example.flowtime.ui.theme.Moonlight
import com.example.flowtime.ui.theme.NightBase
import com.example.flowtime.ui.theme.NightDeep

/**
 * 夜航场景底（Nocturne Glassmorphism 的"场景先于玻璃"）：
 * 深墨蓝 → 近黑的纵向渐变打底，叠 2~3 个大半径柔光光斑
 * （右上月光、左下钢蓝、中下香槟微光），再加 2.5% 噪点颗粒。
 *
 * 玻璃面板都是半透明的，必须有这层"有光的夜景"在下面可借——
 * 光斑全部避开中心内容区，保证正文对比度；整体静止 → 满足 reduced-motion。
 */
@Composable
fun NocturneScene(modifier: Modifier = Modifier) {
    // 场景底色抬亮一档：顶部钢墨蓝 -> 底部深墨蓝（原来的 NightBase->近黑 太黑，
        // 计时页整屏背景尤其明显）
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF141F35), NightBase)))) {
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

            // 光源光斑：月光（右上·亮）+ 钢蓝（左下·深）+ 香槟微光（中下·点缀）
            // + 中央大月光井：柔和地垫在计时圆环后面，让页面中心不再是死黑
            well(Moonlight, 0.50f, 0.40f, 0.85f, 0.12f)
            well(Moonlight, 0.94f, 0.04f, 0.75f, 0.38f)
            well(MoonSteel, 0.06f, 0.96f, 0.70f, 0.46f)
            well(Champagne, 0.55f, 0.72f, 0.45f, 0.18f)
        }
        // 噪点颗粒：128×128 灰度噪点平铺，整体 2.5% 透明度，消除渐变的塑料感
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

/** 程序生成一次的灰度噪点位图 */
private val NoiseBitmap: Bitmap by lazy {
    val size = 128
    val pixels = IntArray(size * size)
    val rnd = java.util.Random(7L)
    for (i in pixels.indices) {
        val g = rnd.nextInt(256)
        pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** 平铺噪点画笔：REPEAT 平铺 + 6/255 整体透明度 */
private val NoisePaint: android.graphics.Paint by lazy {
    android.graphics.Paint().apply {
        shader = android.graphics.BitmapShader(
            NoiseBitmap,
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT,
        )
        alpha = 6
    }
}
