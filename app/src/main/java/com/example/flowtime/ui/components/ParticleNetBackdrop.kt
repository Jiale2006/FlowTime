package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.ParticleBlue
import com.example.flowtime.ui.theme.ParticleCyan
import com.example.flowtime.ui.theme.ParticleNight
import com.example.flowtime.ui.theme.ParticleTeal
import com.example.flowtime.ui.theme.ParticleViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 粒子系统主题的粒子网层（提示词 [PARTICLE LAYER]）：
 * 深蓝黑底 + 漂浮粒子 + **邻近粒子的低透明连线**，营造"数据在空间中流动"。
 *
 * 与 `Effects.kt` 那套环境微光的区别（两套并存会互相抢戏，提示词明令禁止
 * "patterns that compete with particle animation"，故本主题下计时页会关掉那一套）：
 * ① 这里是**漂移 + 边界回绕**，不是上升消散 —— 粒子总量恒定，才有"网络"；
 * ② 有**连线**（本主题的招牌），环境微光只是单点；
 * ③ 数量按提示词给到 50~150 档（默认 72）。
 *
 * 🔴 物理量单位一律 **秒 / px·s⁻¹**，速度与寿命都绑画布尺寸 —— 帧驱动动画必须把
 *    帧间隔折成秒再驱动（历史事故：按"帧"计数时 120Hz 屏上速度翻倍、寿命减半）。
 *
 * 🔴 静止/低动效：`animator_duration_scale == 0` 时只铺一张**静态星图**，
 *    不进帧循环（省电，且满足提示词要求的 reduced-motion 备选）。
 */
private const val NET_DOT_COUNT = 72

/** 连线距离阈值（画布短边比例）。规范未给具体值，取 0.18 —— 只连真正的近邻。 */
private const val LINK_RADIUS_FRACTION = 0.18f

/** 粒子整体亮度增益：规范既要有辉光感、又禁止"粒子的可读性干扰"，压在 0.8。 */
private const val NET_ALPHA_GAIN = 0.80f

/**
 * 一颗网络节点粒子。
 *
 * @param radius 半径（px）：按提示词 1–6px **直径** 折算，视觉尺寸随屏幕密度走。
 * @param phase 明暗呼吸的相位（各粒子错开，否则整屏一起闪）。
 * @param period 呼吸周期（秒）：**刻意不等**（3.2~7.7s），周期相同会同步成"整屏脉动"。
 * @param dim 禁区压制系数（1 = 全亮）：进中心内容区的粒子渐隐，而不是被删掉。
 */
internal class NetDot(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    val phase: Float,
    val period: Float,
    var dim: Float = 1f,
) {
    fun twinkle(clock: Float): Float =
        0.45f + 0.55f * ((1f + sin((clock / period + phase) * 2f * PI)).toFloat() / 2f)
}

class ParticleNetState internal constructor() {
    internal val dots = mutableListOf<NetDot>()
    internal var frameTick by mutableLongStateOf(0L)
        private set
    internal var viewport by mutableStateOf(IntSize.Zero)

    /** 动画时钟（秒）：漂移与闪烁都按它推进 */
    internal var clock = 0f
        private set

    /** 中心内容禁区：圆心纵向比例由调用方给（画布是全屏的，圆心不等于屏幕中心）。 */
    private fun keepOutCenter(w: Int, h: Int, yFraction: Float): Triple<Float, Float, Float> =
        Triple(w / 2f, h * yFraction, minOf(w, h) * 0.21f)

    /**
     * 铺满粒子（只在画布就绪后调一次）。reduced-motion 下这就是最终画面。
     */
    internal fun seed(density: Float, keepOutCenterYFraction: Float) {
        val w = viewport.width
        val h = viewport.height
        if (w == 0 || h == 0) return
        val speed = minOf(w, h) * 0.010f
        while (dots.size < NET_DOT_COUNT) {
            dots.add(newDot(w, h, speed, density))
        }
        updateDims(w, h, keepOutCenterYFraction)
    }

    /**
     * 推进一帧。
     *
     * @param dt 帧间隔，**秒**（已由调用方从纳秒折算并夹住上限，避免掉帧时瞬移）。
     */
    internal fun step(dt: Float, keepOutCenterYFraction: Float) {
        val w = viewport.width
        val h = viewport.height
        if (w == 0 || h == 0) return
        clock += dt
        val margin = 12f
        for (d in dots) {
            d.x += d.vx * dt
            d.y += d.vy * dt
            // 边界回绕：网络感的前提是粒子总量恒定（上升出屏会让画面越来越空）
            if (d.x < -margin) d.x = w + margin
            if (d.x > w + margin) d.x = -margin
            if (d.y < -margin) d.y = h + margin
            if (d.y > h + margin) d.y = -margin
        }
        updateDims(w, h, keepOutCenterYFraction)
        frameTick++
    }

    /**
     * 中心禁区不是"墙"而是"雾"（沿用 Effects.kt 的结论）：粒子飘进内容区不删除，
     * 只把亮度压到 0.12×，到 keepOutR × 1.35 处恢复全亮 ——
     * 既不在时间数字上留亮点，又不会在画面中间挖出一道空洞。
     */
    private fun updateDims(w: Int, h: Int, yFraction: Float) {
        val (cx, cy, keepOutR) = keepOutCenter(w, h, yFraction)
        for (d in dots) {
            val dx = d.x - cx
            val dy = d.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            d.dim = when {
                dist >= keepOutR * 1.35f -> 1f
                dist <= keepOutR -> 0.12f
                else -> 0.12f + 0.88f * ((dist - keepOutR) / (keepOutR * 0.35f))
            }
        }
    }

    private fun newDot(w: Int, h: Int, speed: Float, density: Float): NetDot {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val sp = speed * (0.35f + Random.nextFloat() * 0.85f)
        return NetDot(
            x = Random.nextFloat() * w,
            y = Random.nextFloat() * h,
            vx = cos(angle) * sp,
            vy = sin(angle) * sp,
            // 直径 1.6~6.0 dp（提示词 1–6px）
            radius = (0.8f + Random.nextFloat() * 2.2f) * density,
            color = pickDotColor(),
            phase = Random.nextFloat(),
            period = 3.2f + Random.nextFloat() * 4.5f,
        )
    }

    /** 冷色为主（提示词 Accent: blue / teal / violet），掺少量亮青当"能量节点"。 */
    private fun pickDotColor(): Color {
        val r = Random.nextFloat()
        return when {
            r < 0.10f -> ParticleCyan
            r < 0.46f -> ParticleBlue
            r < 0.72f -> ParticleTeal
            r < 0.90f -> ParticleViolet
            else -> Color(0xFF93C5FD)
        }
    }
}

@Composable
fun rememberParticleNetState(): ParticleNetState = remember { ParticleNetState() }

/**
 * @param keepOutCenterYFraction 中心内容禁区的圆心纵向比例。整页铺满时画布中心
 *        不等于圆环/数字中心，默认取 0.38（计时页圆环所在的纵向位置）。
 */
@Composable
fun ParticleNetBackdrop(
    modifier: Modifier = Modifier,
    keepOutCenterYFraction: Float = 0.38f,
) {
    val density = LocalDensity.current.density
    val reducedMotion = rememberReducedMotion()
    val state = rememberParticleNetState()

    Box(modifier.background(ParticleNight)) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { state.viewport = it }) {
            // 静态氛围光：左上冷蓝 / 右下青。不参与帧循环（动态交给粒子本身），
            // 只是让深蓝黑底不至于死平。终止色用"同色 alpha=0"而非 Transparent —— 后者会泛灰边。
            val radiusA = size.minDimension * 0.78f
            val centerA = Offset(size.width * 0.14f, size.height * 0.10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ParticleBlue.copy(alpha = 0.15f),
                        ParticleBlue.copy(alpha = 0f),
                    ),
                    center = centerA,
                    radius = radiusA,
                ),
                radius = radiusA,
                center = centerA,
            )
            val radiusB = size.minDimension * 0.70f
            val centerB = Offset(size.width * 0.92f, size.height * 0.88f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ParticleTeal.copy(alpha = 0.11f),
                        ParticleTeal.copy(alpha = 0f),
                    ),
                    center = centerB,
                    radius = radiusB,
                ),
                radius = radiusB,
                center = centerB,
            )

            state.frameTick // 订阅帧刷新

            val dots = state.dots
            val linkR = size.minDimension * LINK_RADIUS_FRACTION
            val linkR2 = linkR * linkR
            val hairline = 1.dp.toPx() * 0.75f

            // ① 连线先画（压在粒子之下，避免线头盖住节点）
            //    两端任一在禁区里就跳过 —— 线比点显眼得多，划过数字会很扎眼。
            for (i in dots.indices) {
                val a = dots[i]
                if (a.dim < 0.5f) continue
                for (j in i + 1 until dots.size) {
                    val b = dots[j]
                    if (b.dim < 0.5f) continue
                    val dx = a.x - b.x
                    val dy = a.y - b.y
                    val d2 = dx * dx + dy * dy
                    if (d2 > linkR2) continue
                    val fade = 1f - sqrt(d2) / linkR
                    // 平方衰减：贴得越近越清晰，稍远就几乎看不见（"very low opacity"）
                    drawLine(
                        color = ParticleBlue.copy(alpha = 0.26f * fade * fade),
                        start = Offset(a.x, a.y),
                        end = Offset(b.x, b.y),
                        strokeWidth = hairline,
                    )
                }
            }

            // ② 粒子本体：单点微光，亮度随各自呼吸相位起伏
            for (d in dots) {
                val tw = d.twinkle(state.clock)
                drawCircle(
                    color = d.color.copy(alpha = NET_ALPHA_GAIN * tw * d.dim),
                    radius = d.radius * (0.85f + 0.15f * tw),
                    center = Offset(d.x, d.y),
                )
            }
        }
    }

    LaunchedEffect(reducedMotion) {
        while (state.viewport == IntSize.Zero) {
            withFrameNanos { }
        }
        state.seed(density = density, keepOutCenterYFraction = keepOutCenterYFraction)
        if (reducedMotion) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            withFrameNanos { nowNs ->
                // 纳秒 → 秒；首次视作 0（只补时钟，不产生位移跳跃）。
                // 上限 0.1s：掉帧/后台回前台时不瞬移。
                val dt = if (lastNs == 0L) {
                    0f
                } else {
                    ((nowNs - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
                }
                lastNs = nowNs
                state.step(dt = dt, keepOutCenterYFraction = keepOutCenterYFraction)
            }
        }
    }
}
