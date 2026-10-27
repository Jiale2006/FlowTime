package com.example.flowtime.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 水彩主题「芦苇河」的**活的层面**：显影、水流、岸草、雾气。
 *
 * 2026-10-25 他要求："那层遮挡物（要）全部挡住、消失要细腻"，以及"河水做点特效、岸边植被也动起来"。
 * 所以这里分四件事，全部是**程序化叠加**在原画之上（原画是静态位图，不能改像素）：
 *
 * 1. [RevealMask] —— 显影。整幅画一开始被完全挡住（alpha 0），
 *    然后一团"湿气"从前景草丛向外洇开：底晕（一条连续扩散的边界，保证不留圆点）
 *    + 大量横向椭圆斑（沿纸纤维横向走，不是一个个圆）+ 湿边 + 飞沫。
 *    为了让边缘够细腻又不烧性能，遮罩画在 **200×360 的低分辨率位图上**再双线性放大 ——
 *    放大本身就是一层柔化，比在 1080p 上画上千个径向渐变便宜两个数量级。
 *    重建节流到 ~8Hz（进度和呼吸都足够慢），每帧只多一次 drawImage。
 * 2. 河水 —— 横向流动的高光带 + 偶发涟漪，用 `reed_water_mask.png` 限定范围。
 * 3. 岸草 —— 三条缓慢横扫的柔光带，用 `reed_bank_mask.png` 限定范围，读作"风过草面"。
 * 4. 雾气 —— 天空三团巨大的软光斑，几十秒周期的极慢横向漂移（最出"活"的是它）。
 *
 * 规矩：这里只做**低频、低对比**的运动。水彩画一旦动得明显就假了。
 */

// ── 河水 ─────────────────────────────────────────────────────────────

private class Streak(
    val y: Float,      // 归一化 y
    val len: Float,    // 归一化长度（相对宽）
    val thick: Float,  // 归一化厚度（相对高）
    val speed: Float,  // 相对速度（每秒行进多少个"屏宽"）
    val phase: Float,
    val alpha: Float,
)

private val riverStreaks: List<Streak> = run {
    val rnd = Random(77)
    List(22) {
        Streak(
            y = 0.30f + 0.62f * (it / 21f) + (rnd.nextFloat() - 0.5f) * 0.03f,
            len = 0.10f + rnd.nextFloat() * 0.22f,
            thick = 0.0014f + rnd.nextFloat() * 0.0026f,
            speed = 0.018f + rnd.nextFloat() * 0.028f,
            phase = rnd.nextFloat(),
            alpha = 0.045f + rnd.nextFloat() * 0.065f,
        )
    }
}

private class Ripple(val x: Float, val y: Float, val period: Float, val phase: Float, val maxR: Float)

private val riverRipples: List<Ripple> = run {
    val rnd = Random(451)
    List(4) {
        Ripple(
            x = 0.62f + rnd.nextFloat() * 0.34f,
            y = 0.58f + rnd.nextFloat() * 0.30f,
            period = 6.5f + rnd.nextFloat() * 5.5f,
            phase = rnd.nextFloat(),
            maxR = 0.10f + rnd.nextFloat() * 0.08f,
        )
    }
}

/**
 * 水面的活：横向流动的高光 + 偶发涟漪。
 * [wind] 从风场取（-1..2），风大时高光跑得更快 —— 这一点点耦合很难被"看出来"，
 * 但少了它，水面和芦苇会像两段不相干的素材。
 */
internal fun DrawScope.drawRiverLife(
    mask: ImageBitmap,
    rect: Rect,
    tSec: Float,
    wind: Float,
    strength: Float,
) {
    if (strength <= 0.01f) return
    val flow = 1f + 0.34f * wind
    drawIntoCanvas { c ->
        c.saveLayer(rect, Paint().apply { alpha = strength })
        // ① 高光带：每条的 x 一直向右漂，出界就绕回来
        drawIntoCanvas {
            riverStreaks.forEach { s ->
                val travel = (s.phase + tSec * s.speed * flow) % 1.55f
                val x = rect.left + (travel - 0.28f) * rect.width
                val y = rect.top + s.y * rect.height
                val wpx = s.len * rect.width
                val hpx = (s.thick * rect.height).coerceAtLeast(0.8f)
                drawRoundRect(
                    color = Color.White.copy(alpha = s.alpha),
                    topLeft = Offset(x, y),
                    size = Size(wpx, hpx),
                    cornerRadius = CornerRadius(hpx * 0.5f),
                )
            }
            // ② 涟漪：一圈缓慢扩散再消失的扁椭圆环
            riverRipples.forEach { r ->
                val u = ((tSec / r.period) + r.phase) % 1f
                val rad = r.maxR * rect.width * (0.18f + 0.82f * u)
                val a = (1f - u) * (1f - u) * 0.10f
                if (a > 0.004f) {
                    drawOval(
                        color = Color.White.copy(alpha = a),
                        topLeft = Offset(
                            rect.left + r.x * rect.width - rad,
                            rect.top + r.y * rect.height - rad * 0.30f,
                        ),
                        size = Size(rad * 2f, rad * 0.60f),
                        style = Stroke(width = 1.2f),
                    )
                }
            }
        }
        // ③ 用遮罩裁出水域
        c.saveLayer(rect, Paint().apply { blendMode = BlendMode.DstIn })
        drawImage(
            image = mask,
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
        )
        c.restore()
        c.restore()
    }
}

// ── 雾气 ─────────────────────────────────────────────────────────────

private class Mist(val y: Float, val r: Float, val period: Float, val phase: Float, val alpha: Float, val tint: Color)

private val mists = listOf(
    Mist(y = 0.14f, r = 0.52f, period = 61f, phase = 0.00f, alpha = 0.10f, tint = Color(0xFFFFFBF2)),
    Mist(y = 0.24f, r = 0.40f, period = 47f, phase = 0.35f, alpha = 0.075f, tint = Color(0xFFFFF1EC)),
    Mist(y = 0.075f, r = 0.44f, period = 79f, phase = 0.62f, alpha = 0.085f, tint = Color(0xFFFDF6EE)),
)

/**
 * 天空的雾气缓慢横移（周期 47–79 秒，幅度 ±8% 屏宽）。
 * 这是整幅画"活着"的主力：面积最大、速度最慢，几乎察觉不到，但停下来就能看出来。
 */
internal fun DrawScope.drawMistDrift(rect: Rect, tSec: Float, strength: Float) {
    if (strength <= 0.01f) return
    mists.forEach { m ->
        val u = (tSec / m.period + m.phase) % 1f
        // 三角波：没有起止点，来回都匀速，看不出"绕回去"
        val tri = if (u < 0.5f) u * 2f else 2f - u * 2f
        val cx = rect.left + rect.width * (0.18f + 0.64f * tri + (m.phase - 0.5f) * 0.12f)
        val cy = rect.top + m.y * rect.height
        val r = m.r * rect.width
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    m.tint.copy(alpha = m.alpha * strength),
                    m.tint.copy(alpha = m.alpha * 0.45f * strength),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = r,
            ),
            topLeft = Offset(cx - r, cy - r * 0.42f),
            size = Size(r * 2f, r * 0.84f),
        )
    }
}
