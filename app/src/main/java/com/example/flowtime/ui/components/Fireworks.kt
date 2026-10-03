package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardOrange
import com.example.flowtime.ui.theme.Gold
import com.example.flowtime.ui.theme.PrimaryLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 粒子实体（教程 §6.1）：位移 + 重力 + 透明度衰减 */
internal class FireworkParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var gravity: Float = 0f,
    var radius: Float = 6f,
    var color: Color = Color.White,
    var life: Int = 60,
    var maxLife: Int = 60,
) {
    val alpha: Float get() = if (life <= 0) 0f else life.toFloat() / maxLife

    fun update() {
        vy += gravity
        x += vx
        y += vy
        life--
    }
}

/** 粒子管理器：对象池复用，最大 200 粒（教程 §6.2/§6.5） */
class FireworksState internal constructor() {
    private val pool = ArrayDeque<FireworkParticle>()
    internal val particles = mutableListOf<FireworkParticle>()
    internal var frameTick by mutableLongStateOf(0L)
        private set
    internal var viewport by mutableStateOf(IntSize.Zero)
    var burstSerial by mutableIntStateOf(0)
        private set
    internal var staged by mutableStateOf(false)
    internal val pendingBursts = mutableListOf<FloatArray>()

    /** 触发庆祝烟花（完成页进入时调用，三连错峰） */
    fun celebrate() {
        staged = true
        burstSerial++
    }

    /**
     * 在视口比例位置放一次烟花（供统计页达标纸屑等）。
     * fx/fy 为 0..1 视口比例，实际像素在帧循环拿到视口后解析。
     */
    fun burstAt(fx: Float, fy: Float, count: Int = 50) {
        pendingBursts.add(floatArrayOf(fx, fy, count.toFloat()))
        burstSerial++
    }

    private fun obtain(): FireworkParticle = pool.removeFirstOrNull() ?: FireworkParticle()

    private fun recycle(p: FireworkParticle) {
        if (pool.size < 400) pool.addLast(p)
    }

    internal fun burst(cx: Float, cy: Float, count: Int, scale: Float) {
        val colors = listOf(Gold, AccentGreen, CardOrange, PrimaryLight)
        val n = count.coerceAtMost(200 - particles.size)
        repeat(n) {
            val p = obtain()
            val angle = Random.nextDouble() * 2 * Math.PI
            val speed = (4f + Random.nextFloat() * 10f) * scale
            p.x = cx
            p.y = cy
            p.vx = (cos(angle) * speed).toFloat()
            p.vy = (sin(angle) * speed).toFloat()
            p.gravity = 0.4f * scale
            p.radius = (3f + Random.nextFloat() * 6f) * scale
            p.color = colors.random()
            p.maxLife = 50 + Random.nextInt(40)
            p.life = p.maxLife
            particles.add(p)
        }
    }

    internal fun step() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update()
            if (p.life <= 0) {
                recycle(p)
                iterator.remove()
            }
        }
        frameTick++
    }
}

@Composable
fun rememberFireworksState(): FireworksState = remember { FireworksState() }

/** 全屏烟花覆盖层：帧动画跟随 vsync，粒子耗尽即停 */
@Composable
fun FireworksOverlay(state: FireworksState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.viewport = it }
    ) {
        state.frameTick // 订阅帧刷新驱动重绘
        for (p in state.particles) {
            drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x, p.y),
            )
        }
    }

    LaunchedEffect(state.burstSerial) {
        if (state.burstSerial == 0) return@LaunchedEffect
        while (state.viewport == IntSize.Zero) {
            withFrameNanos { }
        }
        val w = state.viewport.width.toFloat()
        val h = state.viewport.height.toFloat()
        if (state.staged) {
            state.staged = false
            state.burst(w / 2f, h / 3f, 90, density)
            launch {
                delay(280)
                state.burst(w / 4f, h / 2f, 60, density)
            }
            launch {
                delay(520)
                state.burst(w * 3f / 4f, h * 5f / 12f, 60, density)
            }
        }
        while (state.pendingBursts.isNotEmpty()) {
            val (fx, fy, count) = state.pendingBursts.removeFirst()
            state.burst(fx * w, fy * h, count.toInt(), density)
        }
        while (state.particles.isNotEmpty()) {
            withFrameNanos { state.step() }
        }
    }
}
