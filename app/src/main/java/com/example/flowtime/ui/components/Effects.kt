package com.example.flowtime.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Gold
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 系统「减弱动效」检测（animator_duration_scale == 0 时用户已关闭动画）。
 * 命中时调用方应跳过粒子/呼吸/流光等非必要动效。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** 流光扫过（用于已解锁徽章、达标进度条）：对角渐变高光循环扫过 */
fun Modifier.shimmerSweep(
    enabled: Boolean,
    color: Color = Color.White,
): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    drawWithContent {
        drawContent()
        val sweepWidth = size.width * 0.35f
        val x = -sweepWidth + (size.width + sweepWidth * 2) * progress
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.35f), Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + sweepWidth, size.height),
            ),
        )
    }
}

/**
 * 全局呼吸周期（毫秒）：环境粒子的生成节律（呼谷少、吸峰多）用这一份常量。
 * 🔴 它曾由整环光带 / 弧首光晕 / 环境粒子三处共用；那两处特效已按"简洁"要求移除，
 *    现在只剩粒子一处 —— 但常量保留单点定义：将来再加呼吸类动效，必须回到这里共用，
 *    否则又会各写各的周期（历史事故：12s vs 2s，看着像"两个心跳"）。
 */
const val BREATH_PERIOD_MS = 4500f

/** 吸气在整节呼吸里占的时间比例 —— 小于 0.5 即"吸快呼慢"。 */
const val INHALE_FRACTION = 0.35f

/**
 * 由帧时钟（毫秒）算 0..1 呼吸相位（0 = 呼气谷，1 = 吸气峰）。
 *
 * 两处讲究：
 * ① (1-cos)/2 两端缓、中间快，比 sin 更像呼吸（sin 在过零点变化最快，观感偏"闪"）。
 * ② 时间轴先做**非对称 warp**：吸气只占 35%、呼气占 65%。真人呼吸就是吸快呼慢，
 *    等分的正弦看着像心跳而不是呼吸。
 *
 * 帧时钟取自 withFrameNanos 的系统单调时钟 → 同帧内各调用点相位完全一致，天然同频同相。
 * 传负数（做相位滞后）也安全：先归一到 [0, 周期) 再算。
 */
fun breathPhaseAt(frameMs: Long): Float {
    val period = BREATH_PERIOD_MS.toLong()
    val t = ((frameMs % period) + period) % period
    val u = t / BREATH_PERIOD_MS
    val warp = if (u < INHALE_FRACTION) {
        0.5f * u / INHALE_FRACTION
    } else {
        0.5f + 0.5f * (u - INHALE_FRACTION) / (1f - INHALE_FRACTION)
    }
    return ((1.0 - cos(2.0 * Math.PI * warp)) / 2.0).toFloat()
}

/** 环境微光粒子：缓慢上飘、横向摇曳、透明度正弦呼吸 */
/**
 * 单颗环境微光。
 *
 * 🔴 物理量单位一律是**秒 / px·s⁻¹**，不是"帧"：帧循环把真实帧间隔折算成秒再驱动，
 * 且速度绑在**画布高度**上 —— 于是 60/90/120Hz、小画布/全屏行为都一致。
 */
internal class Mote(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var color: Color,
    var life: Float,
    var maxLife: Float,
    var swayPhase: Float,
    /** 禁区压制系数（1 = 全亮，近 0 = 几乎看不见）：进中心禁区的粒子渐隐穿行，而不是被删掉。 */
    var dim: Float = 1f,
) {
    /** 生命周期相位 0→1→0（出生 0 / 中年 1 / 消亡 0）—— alpha 与半径共用这条钟形曲线。 */
    val lifePhase: Float
        get() = sin(Math.PI * (1f - life / maxLife)).toFloat().coerceIn(0f, 1f)

    val alpha: Float get() = lifePhase

    /**
     * 当前半径：出生时小 → 中年最大 → 消亡收小。
     * 原来是"出生多大就一路不变"，配合 alpha 看着像"点忽然出现、又忽然停住"。
     */
    val radiusNow: Float get() = radius * (0.45f + 0.55f * lifePhase)
}

class AmbientParticlesState internal constructor() {
    internal val motes = mutableListOf<Mote>()
    internal var frameTick by mutableLongStateOf(0L)
        private set
    internal var viewport by mutableStateOf(IntSize.Zero)

    /** 生成累积器：呼吸谷值时欠着、吸气峰时补上，数量本身就在"呼吸" */
    private var spawnAcc = 0f

    /**
     * @param dt 帧间隔，以 60fps 的 16.67ms 为 1.0。所有物理量按它缩放 ——
     *           原来全部以"帧"作单位，120Hz 屏上飘升速度翻倍、寿命减半、摇曳频率翻倍。
     * @param breathPeak 当前呼吸相位 0..1（[breathPhaseAt]）：吸气峰集中生成，呼气几乎静止。
     */
    internal fun step(
        spawn: Boolean,
        targetCount: Int,
        intensity: Float,
        density: Float,
        dt: Float,
        breathPeak: Float,
        keepOutCenterYFraction: Float,
    ) {
        val w = viewport.width
        val h = viewport.height
        if (w == 0 || h == 0) return
        // dt 是「标准帧数」（1 = 16.67ms）→ 除以 60 得秒。
        // 粒子物理一律按**秒**算（速度 px/s、寿命 s），速度再绑到画布高度：
        // 画布从 377dp 变全屏、或换一台不同尺寸的机器，行为都不变。
        val dtSec = dt / 60f
        val iterator = motes.iterator()
        // 中心文字禁区：飘到圆环/时间数字区的粒子压暗（见下方 dim），不干扰读数。
        // 🔴 画布改成全屏后，画布中心 ≠ 圆环中心（圆环在内容区约 38% 高度处），
        //    所以禁区圆心的纵向位置由调用方传入 —— 否则禁区挖在屏幕正中，等于没挖。
        val keepOutR = minOf(w, h) * 0.21f
        val cx = w / 2f
        val cy = h * keepOutCenterYFraction
        while (iterator.hasNext()) {
            val m = iterator.next()
            m.life -= dtSec
            m.swayPhase += 3.0f * dtSec          // 3 rad/s → 摇曳周期 ≈ 2.1s
            m.x += (m.vx + sin(m.swayPhase) * w * 0.045f) * dtSec
            m.y += m.vy * dtSec
            if (m.life <= 0f || m.y < -20f || m.x < -60f || m.x > w + 60f) {
                iterator.remove()
                continue
            }
            // 中心禁区不是"墙"而是"雾"：飘进来不删除，只压暗到近乎看不见 ——
            // 全屏保持完整（不再有一道竖着的空洞），又不会在时间数字上留亮点。
            // keepOutR 以内 0.12×，到 1.35× 处恢复到全亮。
            val dx = m.x - cx
            val dy = m.y - cy
            val d = sqrt(dx * dx + dy * dy)
            m.dim = when {
                d >= keepOutR * 1.35f -> 1f
                d <= keepOutR -> 0.12f
                else -> 0.12f + 0.88f * ((d - keepOutR) / (keepOutR * 0.35f))
            }
        }
        if (!spawn) {
            spawnAcc = 0f
            frameTick++
            return
        }
        val target = (targetCount * intensity).toInt()
        // 生成速率随呼吸起伏（谷 0.25× / 峰 1.75×），累积器上限 4 防长时间呼气后成批喷发
        spawnAcc = (spawnAcc + (target - motes.size).coerceAtLeast(0) *
            (0.25f + 1.5f * breathPeak) * dt).coerceAtMost(4f)
        // 每帧最多新生 2 个，避免成批突现
        var born = 0
        while (spawnAcc >= 1f && motes.size < target && born < 2) {
            spawnAcc -= 1f
            born++
            val pick = Random.nextFloat()
            // 上升速度 = 每秒飘过画布高度的 5.5%~8.5%（⇒ 11.8~18.2 秒飘完全屏）。
            // 🔴 必须绑到 h 而不是写死 px：原来的绝对值是按 377dp 画布调的，
            //    画布变全屏（×2.5 高）后同样的速度让粒子只够飘下半 1/4。
            val riseSpeed = h * (0.055f + Random.nextFloat() * 0.030f) *
                intensity.coerceIn(0.5f, 2f)
            val y0 = h * (0.55f + Random.nextFloat() * 0.45f)
            motes.add(
                Mote(
                    x = Random.nextFloat() * w,
                    // 出生点：屏幕 55%~100% 高度（下半部）→ 一路飘过整个画面
                    y = y0,
                    vx = (Random.nextFloat() - 0.5f) * w * 0.008f,
                    vy = -riseSpeed,
                    radius = (1.5f + Random.nextFloat() * 3f) * density,
                    color = when {
                        pick < 0.15f -> Gold
                        pick < 0.6f -> AccentGreen
                        else -> CardGreenLight
                    },
                    // 寿命 = 从出生点飘出屏幕顶部所需时间 × 1.25
                    // （留余量 ⇒ 粒子是"飘出画面"消散，不是"半路凭空消失"）
                    maxLife = y0 / riseSpeed * 1.25f,
                    life = 0f,
                    swayPhase = Random.nextFloat() * 6.28f,
                ).also { it.life = it.maxLife },
            )
        }
        frameTick++
    }
}

@Composable
fun rememberAmbientParticlesState(): AmbientParticlesState = remember { AmbientParticlesState() }

/** 粒子整体亮度系数（0.62）：三色 token 本身较亮，压低一点才像"环境微光"。 */
private const val PARTICLE_ALPHA_GAIN = 0.62f

/**
 * 环境漂浮粒子层：active 时持续生成；active 关闭后已存在粒子自然衰减消失。
 * 帧循环跟随 vsync，粒子耗尽即停。
 *
 * 每颗粒子是**单点微光**（一颗小圆）：纯 `drawCircle`，
 * 半径随生命周期起伏、亮度随呼吸节律，不参与任何堆分配。
 *
 * @param keepOutCenterYFraction 禁区圆心的纵向位置（画布高度比例 0..1）。
 *        粒子铺满整页后画布中心不再是圆环中心，调用方必须传圆环实际所在的纵向比例。
 */
@Composable
fun AmbientParticles(
    state: AmbientParticlesState,
    active: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    targetCount: Int = 18,
    keepOutCenterYFraction: Float = 0.5f,
) {
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.onSizeChanged { state.viewport = it }) {
        state.frameTick // 订阅帧刷新
        for (m in state.motes) {
            // 单点：一颗微光。半径随生命周期起伏（出生小→中年最大→消散收小），
            // 配合 alpha 曲线看着像"光浮现又化开"，而不是一个大小恒定的硬点。
            // dim = 禁区压制（飘到时间数字附近自动变淡）。
            drawCircle(
                color = m.color.copy(alpha = m.alpha * m.dim * PARTICLE_ALPHA_GAIN),
                radius = m.radiusNow,
                center = Offset(m.x, m.y),
            )
        }
    }

    LaunchedEffect(active, intensity) {
        while (state.viewport == IntSize.Zero) {
            withFrameNanos { }
        }
        // 上一帧时间戳，用于算 dt：首次为 0 → 视作 1.0（一个标准帧）
        var lastNs = 0L
        while (active || state.motes.isNotEmpty()) {
            withFrameNanos { nowNs ->
                val dt = if (lastNs == 0L) {
                    1f
                } else {
                    // 毫秒差 / 16.67ms；夹在 0.25~4 之间：掉帧时不瞬移，也不让粒子"长寿"
                    ((nowNs - lastNs) / 16_666_667.0).toFloat().coerceIn(0.25f, 4f)
                }
                lastNs = nowNs
                state.step(
                    spawn = active,
                    targetCount = targetCount,
                    intensity = intensity,
                    density = density,
                    dt = dt,
                    // 呼吸相位取自帧时钟（同帧内各调用点天然同相）
                    breathPeak = breathPhaseAt(nowNs / 1_000_000L),
                    keepOutCenterYFraction = keepOutCenterYFraction,
                )
            }
        }
    }
}
