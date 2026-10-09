package com.example.flowtime.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot

/**
 * 主题径向揭示（Radial Theme Transition）状态中心。
 *
 * 点击主题选项的那一刻：先给主窗口截图（旧主题画面，作底图），
 * 再切主题并记下触点（主窗口 px 坐标）；新主题整层保持原布局，
 * 仅以触点为圆心的圆形蒙版从 0 扩到最远角，露出下层旧画面被逐圈替换。
 * 系统动画缩放为 0（减弱动画）时调用方直接跳过 start()，退化为普通即时切换。
 */
object ThemeReveal {
    var center by mutableStateOf<Offset?>(null)
        private set
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set

    /** 主窗口根 ComposeView（MainActivity 注册），截图用它拿旧主题画面 */
    var rootView: View? = null

    fun start(centerPx: Offset, oldSnapshot: ImageBitmap?) {
        snapshot = oldSnapshot
        center = centerPx
    }

    fun finish() {
        center = null
        snapshot = null
    }

    /** 半分辨率截取主窗口当前画面；未布局/失败返回 null（退化为纯裁切揭示） */
    fun captureRoot(): ImageBitmap? {
        val v = rootView ?: return null
        if (v.width == 0 || v.height == 0) return null
        return runCatching {
            val bmp = Bitmap.createBitmap(v.width / 2 + 1, v.height / 2 + 1, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(0.5f, 0.5f)
            v.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * 挂到应用最外层：揭示进行中把整层裁进从触点扩出的圆里，动画结束自动解除。
 * 半径按"触点到最远视口角"计算，保证圆总能完整铺满四角。
 */
@Composable
fun Modifier.themeRadialReveal(): Modifier {
    val center = ThemeReveal.center ?: return this
    val progress = remember(center) { Animatable(0f) }
    LaunchedEffect(center) {
        // 780ms + ease-in（先慢后快，cubic 0.42,0,1,1）：起势蓄力、圆越扩越快冲满四角。
        // 用户逐档实测调速：1100 → 800 → 700 → 750 → 780
        progress.animateTo(1f, tween(780, easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)))
        ThemeReveal.finish()
    }
    return this.then(
        Modifier.drawWithContent {
            val content = this
            val p = progress.value
            if (p < 1f) {
                val cx = center.x
                val cy = center.y
                val maxR = maxOf(
                    hypot(cx, cy),
                    hypot(size.width - cx, cy),
                    hypot(cx, size.height - cy),
                    hypot(size.width - cx, size.height - cy),
                )
                val r = (0.06f + 0.94f * p) * maxR
                clipPath(Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }) {
                    content.drawContent()
                }
            } else {
                content.drawContent()
            }
        },
    )
}
