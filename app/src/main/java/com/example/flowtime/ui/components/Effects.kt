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
import kotlin.math.sin
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

/** 环境微光粒子：缓慢上飘、横向摇曳、透明度正弦呼吸 */
internal class Mote(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var color: Color,
    var life: Int,
    var maxLife: Int,
    var swayPhase: Float,
) {
    val alpha: Float
        get() = sin(Math.PI * (1f - life.toFloat() / maxLife)).toFloat().coerceIn(0f, 1f)
}

class AmbientParticlesState internal constructor() {
    internal val motes = mutableListOf<Mote>()
    internal var frameTick by mutableLongStateOf(0L)
        private set
    internal var viewport by mutableStateOf(IntSize.Zero)

    internal fun step(spawn: Boolean, targetCount: Int, intensity: Float, density: Float) {
        val w = viewport.width
        val h = viewport.height
        if (w == 0 || h == 0) return
        val iterator = motes.iterator()
        while (iterator.hasNext()) {
            val m = iterator.next()
            m.life--
            m.swayPhase += 0.05f
            m.x += m.vx + sin(m.swayPhase) * 0.3f * density
            m.y += m.vy
            if (m.life <= 0 || m.y < -20f) iterator.remove()
        }
        if (spawn) {
            val target = (targetCount * intensity).toInt()
            // 每帧最多新生 2 个，避免成批突现
            repeat((target - motes.size).coerceIn(0, 2)) {
                val pick = Random.nextFloat()
                motes.add(
                    Mote(
                        x = Random.nextFloat() * w,
                        y = h * (0.55f + Random.nextFloat() * 0.45f),
                        vx = (Random.nextFloat() - 0.5f) * 0.3f * density,
                        vy = -(0.25f + Random.nextFloat() * 0.55f) * density * intensity.coerceIn(0.5f, 2f),
                        radius = (1.5f + Random.nextFloat() * 3f) * density,
                        color = when {
                            pick < 0.15f -> Gold
                            pick < 0.6f -> AccentGreen
                            else -> CardGreenLight
                        },
                        maxLife = 140 + Random.nextInt(160),
                        life = 0,
                        swayPhase = Random.nextFloat() * 6.28f,
                    ).also { it.life = it.maxLife },
                )
            }
        }
        frameTick++
    }
}

@Composable
fun rememberAmbientParticlesState(): AmbientParticlesState = remember { AmbientParticlesState() }

/**
 * 环境漂浮粒子层：active 时持续生成；active 关闭后已存在粒子自然衰减消失。
 * 帧循环跟随 vsync，粒子耗尽即停。
 */
@Composable
fun AmbientParticles(
    state: AmbientParticlesState,
    active: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    targetCount: Int = 18,
) {
    val density = LocalDensity.current.density

    Canvas(modifier = modifier.onSizeChanged { state.viewport = it }) {
        state.frameTick // 订阅帧刷新
        for (m in state.motes) {
            drawCircle(
                color = m.color.copy(alpha = m.alpha * 0.55f),
                radius = m.radius,
                center = Offset(m.x, m.y),
            )
        }
    }

    LaunchedEffect(active, intensity) {
        while (state.viewport == IntSize.Zero) {
            withFrameNanos { }
        }
        while (active || state.motes.isNotEmpty()) {
            withFrameNanos {
                state.step(spawn = active, targetCount = targetCount, intensity = intensity, density = density)
            }
        }
    }
}
