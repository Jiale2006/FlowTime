package com.example.flowtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.Champagne
import com.example.flowtime.ui.theme.ChampagneBright
import com.example.flowtime.ui.theme.ErrorRed
import com.example.flowtime.ui.theme.GlassBorder
import com.example.flowtime.ui.theme.GlassBorderHigh
import com.example.flowtime.ui.theme.GlassBottomShade
import com.example.flowtime.ui.theme.GlassFillHigh
import com.example.flowtime.ui.theme.Moonlight
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.ThemeMode
import com.example.flowtime.ui.theme.WarningAmber
import com.example.flowtime.ui.theme.WatercolorInk
import com.example.flowtime.ui.theme.WatercolorRose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 环形倒计时（教程 §3.1）：底色弧 + 渐变进度弧，圆角线帽，弧首圆点，中央大号时间。
 *
 * 特效按当前主题分支，各有各的材质语言：
 * - 水彩画风：颜料随剩余时间流动（青绿→蓝灰→玫瑰警示）+ 补笔虚线渗色 + 边缘沉淀
 *   + 弧首湿点晕染（随机掉落晕染斑，像颜料滴进湿纸）；
 * - 玻璃拟态：月光钢蓝→香槟金（越紧迫越金亮）+ 镜面高光沿环缘缓慢扫过
 *   + 笔尖玻璃闪光（亮十字光斑）；
 * - 手绘涂鸦：蓝绿蜡笔→墨色→红笔警示 + 蜡笔颗粒纹理 + 笔尖弹出蜡笔屑
 *   + 手绘抖动圈涟漪（不是正圆）。
 *
 * 全部纯 Canvas 绘制（不用图层，防页面切换残影）；暂停即止，遵守液态缓动。
 *
 * 动效节奏与时钟字形也随主题走：水彩=液态缓动（≥500ms ease-in-out）+ 衬线斜体数字；
 * 其余主题的节奏待各自提示词到位后单独调制。
 */
@Composable
fun RingTimer(
    progress: Float,
    timeText: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    timeColor: Color = TextPrimary,
    breathing: Boolean = false,
    /** 暂停态：整体降透明度，形成明确状态区分 */
    dimmed: Boolean = false,
    /** 秒级涟漪：当前秒内进度 0..1，null 不绘制 */
    rippleFraction: Float? = null,
    /**
     * 颜料色用的进度（0..1）。与 progress 分开：待机时几何上画满圈
     * （progress=1 作为"画布"），但颜料应显示起步色，而不是误判成"时间将尽"。
     */
    pigmentProgress: Float = progress,
    centerContent: (@Composable () -> Unit)? = null,
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)

    // ---- 主题动效参数：水彩=液态缓动（≥500ms ease-in-out，禁快速缓动）----
    // 其余主题的节奏等各自提示词到位后再单独调制。
    val mode = ActivePalette.mode
    val dimFadeMs = when (mode) {
        // 玻璃拟态禁 duration-100/150：状态淡入淡出一律 500ms 流体缓动
        ThemeMode.WATERCOLOR, ThemeMode.NOCTURNE -> 600
        else -> 300
    }
    val ringAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.55f else 1f,
        animationSpec = tween(dimFadeMs, easing = FastOutSlowInEasing),
        label = "ringDim",
    )

    // ---- 颜料色：随剩余时间流动（颜料进度 0→1 即剩余时间 1→0）----
    val pigmentBase = run {
        val pigmentP = pigmentProgress.coerceIn(0f, 1f)
        val settled = lerp(AccentGreen, Primary, (pigmentP / 0.85f).coerceIn(0f, 1f))
        val urgent = ((pigmentP - 0.88f) / 0.12f).coerceIn(0f, 1f)
        // 玻璃拟态的紧迫色是"更亮的香槟"（唯一强调色），水彩/涂鸦用玫瑰/红笔
        val urgentColor = if (mode == ThemeMode.NOCTURNE) ChampagneBright else WatercolorRose
        lerp(settled, urgentColor, urgent * 0.75f)
    }
    val pigmentLight = lerp(pigmentBase, PrimaryLight, 0.45f)

    // ---- 笔尖粒子池（帧驱动；暂停/结束即清空，像笔离开了纸）----
    var blooms by remember { mutableStateOf(emptyList<HeadBloom>()) }
    var frameNowMs by remember { mutableStateOf(0L) }
    val latestProgress by rememberUpdatedState(pigmentProgress)
    LaunchedEffect(breathing) {
        if (!breathing) {
            blooms = emptyList()
            return@LaunchedEffect
        }
        val gapMul = when (ActivePalette.mode) {
            ThemeMode.NOCTURNE -> 0.55f
            ThemeMode.DOODLE -> 0.65f
            else -> 1f
        }
        var lastSpawn = 0L
        var nextGap = 1200L
        while (true) {
            withFrameNanos { nowNanos ->
                val now = nowNanos / 1_000_000L
                frameNowMs = now
                if (now - lastSpawn >= nextGap) {
                    val angle = Math.toRadians(-90.0 + 360.0 * latestProgress).toFloat()
                    blooms = blooms + HeadBloom(now, angle, Random.nextInt())
                    lastSpawn = now
                    nextGap = ((2300L + Random.nextLong(1700L)) * gapMul).toLong()
                }
                val life = bloomLifetime(ActivePalette.mode)
                if (blooms.isNotEmpty() && now - blooms.first().bornAtMillis > life) {
                    blooms = blooms.filter { now - it.bornAtMillis <= life }
                }
            }
        }
    }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        if (breathing) {
            RingGlow(strokePx = strokePx, tint = pigmentBase)
        }
        // 不再用 graphicsLayer 承载整体透明度：整块图层在页面切换时容易留下残影。
        // 改成把 ringAlpha 直接乘进每次绘制的颜色里 —— 视觉一致，但不需要图层。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringA = ringAlpha
            val inset = strokePx / 2 + strokePx * 0.9f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            val doodle = mode == ThemeMode.DOODLE

            // 秒级涟漪：每秒从环缘向外扩散一圈细波（涂鸦主题是手绘抖动圈）
            rippleFraction?.let { fraction ->
                val rippleColor = pigmentBase.copy(alpha = (1f - fraction) * 0.22f * ringA)
                if (doodle) {
                    val rippleR = radius + strokePx * (0.8f + 2.4f * fraction)
                    val wob = Path()
                    val steps = 30
                    for (s in 0..steps) {
                        val a = s / steps.toFloat() * 2f * PI.toFloat()
                        val rr = rippleR + sin(a * 7f) * strokePx * 0.07f
                        val x = center.x + rr * cos(a)
                        val y = center.y + rr * sin(a)
                        if (s == 0) wob.moveTo(x, y) else wob.lineTo(x, y)
                    }
                    wob.close()
                    drawPath(wob, color = rippleColor, style = Stroke(strokePx * 0.12f, cap = StrokeCap.Round))
                } else {
                    drawCircle(
                        color = rippleColor,
                        radius = radius + strokePx * (0.8f + 2.4f * fraction),
                        center = center,
                        style = Stroke(width = strokePx * 0.14f),
                    )
                }
            }

            // 背景装饰点（示例图环形周围的散点）
            val dotPositions = listOf(
                0.12f to 0.08f, 0.86f to 0.12f, 0.95f to 0.42f, 0.90f to 0.78f,
                0.08f to 0.34f, 0.05f to 0.72f, 0.30f to 0.94f, 0.70f to 0.95f,
            )
            dotPositions.forEachIndexed { index, (fx, fy) ->
                drawCircle(
                    color = if (index % 2 == 0) {
                        AccentGreen.copy(alpha = 0.35f * ringA)
                    } else if (mode == ThemeMode.NOCTURNE) {
                        // 玻璃主题装饰点不上色：月光 + 白 15%
                        GlassBorder.copy(alpha = ringA)
                    } else {
                        CardGreenLight.copy(alpha = ringA)
                    },
                    radius = strokePx * (if (index % 3 == 0) 0.30f else 0.18f),
                    center = Offset(size.width * fx, size.height * fy),
                )
            }

            // 刻度线（12 条，弱化）
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) - 90.0)
                val outer = radius + strokePx * 0.9f
                val inner = outer - strokePx * 0.55f
                drawLine(
                    color = AccentGreen.copy(alpha = 0.4f * ringA),
                    start = Offset(
                        center.x + (inner * cos(angle)).toFloat(),
                        center.y + (inner * sin(angle)).toFloat(),
                    ),
                    end = Offset(
                        center.x + (outer * cos(angle)).toFloat(),
                        center.y + (outer * sin(angle)).toFloat(),
                    ),
                    strokeWidth = strokePx * 0.10f,
                    cap = StrokeCap.Round,
                )
            }

            // 底色弧。玻璃拟态 = 无色空玻璃管：白 8% 管体 + 内外管壁细边 + 顶缘受光高光
            if (mode == ThemeMode.NOCTURNE) {
                drawArc(
                    color = GlassFillHigh.copy(alpha = 0.5f * ringA),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                // 管壁（内外两条白 15% 细边 = 玻璃厚度）
                fun wallArc(insetAdj: Float, alpha: Float) {
                    val ins = inset + insetAdj
                    drawArc(
                        color = GlassBorder.copy(alpha = alpha * ringA),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(ins, ins),
                        size = Size(size.width - ins * 2, size.height - ins * 2),
                        style = Stroke(width = strokePx * 0.07f),
                    )
                }
                wallArc(-strokePx * 0.5f, 1f)
                wallArc(strokePx * 0.5f, 0.7f)
                // 顶缘受光：上半环外壁一条渐弱白高光（光从上方来）
                drawArc(
                    color = GlassBorderHigh.copy(alpha = 0.55f * ringA),
                    startAngle = -150f, sweepAngle = 120f, useCenter = false,
                    topLeft = Offset(inset - strokePx * 0.5f, inset - strokePx * 0.5f),
                    size = Size(
                        size.width - (inset - strokePx * 0.5f) * 2,
                        size.height - (inset - strokePx * 0.5f) * 2,
                    ),
                    style = Stroke(width = strokePx * 0.14f, cap = StrokeCap.Round),
                )
            } else {
                drawArc(
                    color = CardGreenLight.copy(alpha = 0.7f * ringA),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }

            // 渐变进度弧（从顶部顺时针）：半透明叠色
            if (clamped > 0f) {
                val sweep = 360f * clamped
                val gradient = Brush.sweepGradient(
                    colors = listOf(pigmentLight, pigmentBase, pigmentLight),
                    center = center,
                )
                // 主笔触（玻璃拟态走"管内液体光"专属分支，不叠通用主笔）
                if (mode != ThemeMode.NOCTURNE) {
                    drawArc(
                        brush = gradient,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        alpha = 0.72f * ringA,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }

                when (mode) {
                    // 水彩：两道错位虚线的"补笔" + 内外缘沉淀细弧（渗色与边缘沉积）
                    ThemeMode.WATERCOLOR -> {
                        drawArc(
                            brush = gradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.36f * ringA,
                            style = Stroke(
                                width = strokePx * 0.55f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(strokePx * 2.4f, strokePx * 1.6f),
                                ),
                            ),
                        )
                        drawArc(
                            brush = gradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.30f * ringA,
                            style = Stroke(
                                width = strokePx * 0.34f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(strokePx * 1.3f, strokePx * 1.9f),
                                    strokePx * 1.7f,
                                ),
                            ),
                        )
                        val edgeInsetOut = inset - strokePx * 0.30f
                        val edgeInsetIn = inset + strokePx * 0.30f
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(edgeInsetOut, edgeInsetOut),
                            size = Size(size.width - edgeInsetOut * 2, size.height - edgeInsetOut * 2),
                            alpha = 0.26f * ringA,
                            style = Stroke(width = strokePx * 0.15f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(edgeInsetIn, edgeInsetIn),
                            size = Size(size.width - edgeInsetIn * 2, size.height - edgeInsetIn * 2),
                            alpha = 0.20f * ringA,
                            style = Stroke(width = strokePx * 0.15f, cap = StrokeCap.Round),
                        )
                    }

                    // 玻璃拟态：管内液体光（月光蓝，越紧迫越香槟）+ 管壁镜面细线 +
                    // 白色扫光沿环缘缓行（玻璃只借光不上色：扫光不用香槟）
                    ThemeMode.NOCTURNE -> {
                        val liquidBrush = Brush.sweepGradient(
                            colors = listOf(
                                pigmentBase.copy(alpha = 0.40f),
                                pigmentBase.copy(alpha = 0.92f),
                                lerp(pigmentBase, Moonlight, 0.5f).copy(alpha = 0.70f),
                            ),
                            center = center,
                        )
                        drawArc(
                            brush = liquidBrush,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = ringA,
                            style = Stroke(width = strokePx * 0.78f, cap = StrokeCap.Round),
                        )
                        // 管壁镜面细线：贴着液体偏外缘的一道白（管截面的受光反射）
                        val specIns = inset - strokePx * 0.16f
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(specIns, specIns),
                            size = Size(size.width - specIns * 2, size.height - specIns * 2),
                            alpha = 0.22f * ringA,
                            style = Stroke(width = strokePx * 0.10f, cap = StrokeCap.Round),
                        )
                        // 背光暗缘：液体下缘一线近黑（inset bottom shade 的弧形版）
                        val shadeIns = inset + strokePx * 0.24f
                        drawArc(
                            color = GlassBottomShade,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(shadeIns, shadeIns),
                            size = Size(size.width - shadeIns * 2, size.height - shadeIns * 2),
                            alpha = 0.5f * ringA,
                            style = Stroke(width = strokePx * 0.12f, cap = StrokeCap.Round),
                        )
                        // 扫光高光：沿环缘缓慢游走的白色反光带（借场景的光，非自身颜色）
                        val phase = (frameNowMs % 9000L) / 9000f
                        val sweepCenterDeg = -90f + 360f * phase
                        drawArc(
                            color = GlassBorderHigh,
                            startAngle = sweepCenterDeg - 12f,
                            sweepAngle = 24f,
                            useCenter = false,
                            topLeft = Offset(inset + strokePx * 0.12f, inset + strokePx * 0.12f),
                            size = Size(
                                size.width - (inset + strokePx * 0.12f) * 2,
                                size.height - (inset + strokePx * 0.12f) * 2,
                            ),
                            alpha = 0.10f * ringA,
                            style = Stroke(width = strokePx * 1.15f, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = GlassBorderHigh,
                            startAngle = sweepCenterDeg - 10f,
                            sweepAngle = 20f,
                            useCenter = false,
                            topLeft = Offset(inset - strokePx * 0.08f, inset - strokePx * 0.08f),
                            size = Size(
                                size.width - (inset - strokePx * 0.08f) * 2,
                                size.height - (inset - strokePx * 0.08f) * 2,
                            ),
                            alpha = 0.30f * ringA,
                            style = Stroke(width = strokePx * 0.38f, cap = StrokeCap.Round),
                        )
                    }

                    // 手绘涂鸦：两道短虚线叠出蜡笔颗粒（蜡笔的粗粝感）
                    ThemeMode.DOODLE -> {
                        drawArc(
                            brush = gradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.45f * ringA,
                            style = Stroke(
                                width = strokePx * 0.8f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(strokePx * 0.9f, strokePx * 0.45f),
                                ),
                            ),
                        )
                        drawArc(
                            brush = gradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.35f * ringA,
                            style = Stroke(
                                width = strokePx * 0.5f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(strokePx * 0.5f, strokePx * 0.7f),
                                    strokePx * 0.6f,
                                ),
                            ),
                        )
                    }
                }

                // 弧首圆点（涂鸦主题补一个错位的"重描"点，像画了两笔）
                val headAngle = Math.toRadians((-90f + 360f * clamped).toDouble())
                val headCenter = Offset(
                    center.x + (radius * cos(headAngle)).toFloat(),
                    center.y + (radius * sin(headAngle)).toFloat(),
                )
                if (mode == ThemeMode.NOCTURNE) {
                    // 液面前缘 = 香槟金游标（全环唯一香槟金定点）：光晕 + 指环 + 亮核
                    drawCircle(
                        color = Champagne.copy(alpha = 0.16f * ringA),
                        radius = strokePx * 0.85f,
                        center = headCenter,
                    )
                    drawCircle(
                        color = Champagne.copy(alpha = 0.9f * ringA),
                        radius = strokePx * 0.46f,
                        center = headCenter,
                        style = Stroke(strokePx * 0.12f),
                    )
                    drawCircle(
                        color = ChampagneBright.copy(alpha = 0.95f * ringA),
                        radius = strokePx * 0.26f,
                        center = headCenter,
                    )
                } else {
                    drawCircle(
                        color = pigmentBase.copy(alpha = 0.75f * ringA),
                        radius = strokePx * 0.52f,
                        center = headCenter,
                    )
                    if (doodle) {
                        drawCircle(
                            color = pigmentBase.copy(alpha = 0.35f * ringA),
                            radius = strokePx * 0.48f,
                            center = headCenter + Offset(strokePx * 0.07f, strokePx * 0.06f),
                        )
                    }
                }
                // 弧首彗尾：沿弧向后的渐隐拖尾，制造运动质感（玻璃主题用游标定点，不拖尾）
                if (clamped > 0.02f && mode != ThemeMode.NOCTURNE) {
                    for (k in 1..8) {
                        val trailAngle = headAngle - k * 0.05
                        drawCircle(
                            color = pigmentBase.copy(alpha = (1f - k / 9f) * 0.28f * ringA),
                            radius = strokePx * (0.5f - k * 0.04f),
                            center = Offset(
                                center.x + (radius * cos(trailAngle)).toFloat(),
                                center.y + (radius * sin(trailAngle)).toFloat(),
                            ),
                        )
                    }
                }
            }

            // 笔尖粒子：水彩=湿点晕染 / 玻璃=闪光 / 涂鸦=蜡笔屑
            if (blooms.isNotEmpty()) {
                val now = frameNowMs
                val life = bloomLifetime(mode).toFloat()
                blooms.forEach { bloom ->
                    val age = (now - bloom.bornAtMillis).coerceAtLeast(0L)
                    val t = (age / life).coerceIn(0f, 1f)
                    val appear = (t * 5f).coerceAtMost(1f)
                    val fade = 1f - t
                    val cx = center.x + (radius * cos(bloom.angleRad)).toFloat()
                    val cy = center.y + (radius * sin(bloom.angleRad)).toFloat()
                    val rnd = Random(bloom.seed)
                    when (mode) {
                        ThemeMode.NOCTURNE -> drawGlassGlint(cx, cy, t, fade, strokePx, rnd, ringA)
                        ThemeMode.DOODLE -> drawCrayonSpecks(cx, cy, t, fade, strokePx, rnd, ringA)
                        ThemeMode.WATERCOLOR -> drawWetBloom(cx, cy, t, appear, fade, strokePx, pigmentBase, rnd, ringA)
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 主题节奏：水彩=500ms 液态晕开；玻璃=500ms 流体（禁 duration-100/150）；涂鸦保留快速淡切
            val tickFadeMs = when (mode) {
                ThemeMode.WATERCOLOR, ThemeMode.NOCTURNE -> 500
                else -> 120
            }
            // 时钟容器宽度钉死在最宽组合上：AnimatedContent 交替时容器会在新旧
            // 字符串宽度之间动画收缩（衬线数字不等宽），一收缩旧数字右缘就被裁掉
            // ——表现为跳秒瞬间"最右边被遮挡"。钉死宽度后新旧两帧在同一容器里
            // 交叉淡化，顺带消除了每秒的水平抖动。
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val clockWidthDp = remember(mode, density) {
                val clockStyle = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (mode == ThemeMode.WATERCOLOR) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                )
                val widestPx = maxOf(
                    textMeasurer.measure("88:88", clockStyle).size.width,
                    textMeasurer.measure("888:88", clockStyle).size.width,
                )
                with(density) { widestPx.toDp() } + 8.dp
            }
            // 数字跳动（跳秒脉冲缩放）已按用户要求移除：每秒放大回缩看着心悸。
            // 保留固定宽度容器与水墨晕影；跳秒时只有文字内容静态更新。

            Box {
                AnimatedContent(
                    targetState = timeText,
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        fadeIn(tween(tickFadeMs, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(tickFadeMs, easing = FastOutSlowInEasing))
                    },
                    label = "timeTick",
                ) { text ->
                    if (mode == ThemeMode.WATERCOLOR) {
                        // 数字水墨感：同色低透明度错位副本垫在主数字后面，
                        // 像湿墨在纸上洇出的边（对应预览页 fx「数字水墨感」）
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ClockText(
                                text = text,
                                color = WatercolorInk.copy(alpha = 0.12f),
                                italic = true,
                                modifier = Modifier
                                    .width(clockWidthDp)
                                    .offset(x = 4.dp, y = 3.dp)
                                    // RenderEffect 模糊，API 31+ 生效；低版本退化为硬边错位影
                                    .blur(1.5.dp),
                            )
                            ClockText(
                                text = text,
                                color = timeColor,
                                italic = true,
                                modifier = Modifier.width(clockWidthDp),
                            )
                        }
                    } else {
                        ClockText(
                            text = text,
                            color = timeColor,
                            italic = false,
                            modifier = Modifier.width(clockWidthDp),
                        )
                    }
                }
            }
            centerContent?.invoke()
        }
    }
}

/**
 * 时钟数字：衬线 Light + 宽字距。水彩走斜体（风格规则「文字使用衬线斜体」），
 * 其余主题正体，等各自提示词到位后再调。
 */
@Composable
private fun ClockText(
    text: String,
    color: Color,
    italic: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Serif,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp,
        color = color,
    )
}

/** 呼吸光环：环外围两层柔光随 6s 周期脉动（比正常呼吸慢一拍，进入减速曲线） */
@Composable
private fun RingGlow(strokePx: Float, tint: Color) {
    val transition = rememberInfiniteTransition(label = "ringGlow")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inset = strokePx / 2 + strokePx * 0.9f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = tint.copy(alpha = 0.10f + 0.10f * breath),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx * (2.0f + 0.5f * breath), cap = StrokeCap.Round),
        )
        drawArc(
            color = tint.copy(alpha = 0.05f + 0.07f * breath),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx * (3.2f + 0.8f * breath), cap = StrokeCap.Round),
        )
    }
}

// ---- 笔尖粒子（按主题分支）------------------------------------------------

/** 水彩：湿点晕染——几个错位圆叠成不规则晕染斑，先洇开再淡去 */
private fun DrawScope.drawWetBloom(
    cx: Float,
    cy: Float,
    t: Float,
    appear: Float,
    fade: Float,
    strokePx: Float,
    pigment: Color,
    rnd: Random,
    ringA: Float,
) {
    val bloomR = strokePx * (0.55f + 0.85f * (1f - (1f - t) * (1f - t)))
    repeat(3) {
        val off = strokePx * (0.22f + 0.20f * rnd.nextFloat())
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val rr = bloomR * (0.62f + 0.38f * rnd.nextFloat())
        drawCircle(
            color = pigment.copy(
                alpha = 0.16f * fade * appear * (0.75f + 0.4f * rnd.nextFloat()) * ringA,
            ),
            radius = rr,
            center = Offset(cx + off * cos(a), cy + off * sin(a)),
        )
    }
}

/** 玻璃拟态：玻璃闪光——亮心 + 十字光斑，快闪快灭 */
private fun DrawScope.drawGlassGlint(
    cx: Float,
    cy: Float,
    t: Float,
    fade: Float,
    strokePx: Float,
    rnd: Random,
    ringA: Float,
) {
    val glintColor = lerp(ChampagneBright, com.example.flowtime.ui.theme.Moonlight, rnd.nextFloat())
    val drift = strokePx * 0.35f * easeOut(t)
    val cx2 = cx + drift * (rnd.nextFloat() - 0.5f)
    val cy2 = cy + drift * (rnd.nextFloat() - 0.5f)
    drawCircle(
        color = glintColor.copy(alpha = 0.55f * fade * ringA),
        radius = strokePx * 0.22f,
        center = Offset(cx2, cy2),
    )
    val len = strokePx * (0.45f + 0.35f * easeOut(t))
    drawLine(
        color = glintColor.copy(alpha = 0.38f * fade * ringA),
        start = Offset(cx2 - len, cy2),
        end = Offset(cx2 + len, cy2),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = glintColor.copy(alpha = 0.38f * fade * ringA),
        start = Offset(cx2, cy2 - len),
        end = Offset(cx2, cy2 + len),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round,
    )
}

/** 手绘涂鸦：蜡笔屑——黄/红/蓝绿三色小方屑从笔尖四散弹出 */
private fun DrawScope.drawCrayonSpecks(
    cx: Float,
    cy: Float,
    t: Float,
    fade: Float,
    strokePx: Float,
    rnd: Random,
    ringA: Float,
) {
    val markerColors = listOf(AccentGreen, WarningAmber, ErrorRed)
    repeat(5) {
        val dir = rnd.nextFloat() * 2f * PI.toFloat()
        val dist = strokePx * (0.25f + 0.55f * easeOut(t) * (0.5f + 0.5f * rnd.nextFloat()))
        val side = strokePx * 0.16f * (1f - t * 0.4f)
        val color = markerColors[rnd.nextInt(markerColors.size)]
        rotate(degrees = rnd.nextFloat() * 90f, pivot = Offset(cx, cy)) {
            drawRect(
                color = color.copy(alpha = 0.55f * fade * ringA),
                topLeft = Offset(cx + dist * cos(dir), cy + dist * sin(dir)),
                size = Size(side, side),
            )
        }
    }
}

private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

private fun bloomLifetime(mode: ThemeMode): Long = when (mode) {
    ThemeMode.NOCTURNE -> 1600L
    ThemeMode.DOODLE -> 2200L
    ThemeMode.WATERCOLOR -> 3800L
}

/** 笔尖粒子：出生时间 + 笔尖角度 + 形状种子（同种子形状稳定不闪） */
private data class HeadBloom(
    val bornAtMillis: Long,
    val angleRad: Float,
    val seed: Int,
)
