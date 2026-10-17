package com.example.flowtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.ChampagneBright
import com.example.flowtime.ui.theme.MonoGray200
import com.example.flowtime.ui.theme.MonoInk
import com.example.flowtime.ui.theme.ParticleBlue
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.ThemeMode
import com.example.flowtime.ui.theme.WatercolorInk
import com.example.flowtime.ui.theme.WatercolorRose
import kotlin.math.cos
import kotlin.math.sin

/**
 * 单色极简的计时线宽：**计时就是单色的一根线**（用户 2026-10-16 定），
 * 与其余主题 14dp 的"管"无关，故单独定义，不跟随 strokeWidth 形参。
 */
private val MonoLineWidth = 2.dp

/**
 * 粒子系统的环线宽：**冷蓝细环**（用户 2026-10-16 选的 2B 方案 —— 精密仪器感，
 * 不是其余主题 14dp 的"管"），同样与 strokeWidth 形参无关，单独定义。
 */
private val ParticleLineWidth = 3.dp

/**
 * 玻璃拟态的环线宽（2026-10-17 按"完全苹果"改）：Apple 计时器的环是**一条等宽细线**，
 * 不是 14dp 的管。直径约 280dp 的环上 Apple 的比例接近 3%，取 8dp。
 * 与 [MonoLineWidth]/[ParticleLineWidth] 一样，不跟随 strokeWidth 形参。
 */
private val NocturneLineWidth = 8.dp

/**
 * 环形倒计时（教程 §3.1）：底色弧 + 渐变进度弧，圆角线帽，弧首定位点，中央大号时间。
 *
 * 🔴 环区为**纯静态**绘制（用户要求"简洁"，2026-10-14 定）：整环光带 / 环境粒子 / 秒级涟漪 /
 *    进度内流光 / 弧首彗尾 / 背景散点 / 刻度线 已全部移除。
 *    保留的只是各主题的**静态材质差异**（如玻璃的管内液体光 + 镜面细线、水彩的补笔渗色）。
 *
 * 全部纯 Canvas 绘制（不用图层，防页面切换残影）。
 * 🔴 已无帧驱动循环（原来那个 `while(true) + withFrameNanos` 随着笔尖粒子一起删了）——
 *    环形区不再持续重绘，只在 progress 变化时重组。
 *
 * 主题字形差异：水彩 = 衬线斜体数字 + 水墨洇边；其余主题 = 直立衬线。
 * 单色极简的环 = **一根 2dp 墨黑线**：极浅灰底轨 + 墨黑进度线，无端点标记、恒色不偏色。
 *
 * ⚠️ **水彩根本不经过本组件**：`TimerScreen.kt` 里 `mode == WATERCOLOR` 走的是 `ReedPaintingTimer`。
 *    所以本文件内所有 `ThemeMode.WATERCOLOR` 分支都是**死代码**（`dimFadeMs` 条目 / Canvas 水彩补笔 /
 *    `tickFadeMs` 的 500 / 时钟字体 / 数字水墨洇影副本，共 5 处）。**保留 ≠ 有效**，
 *    不要照它们推导水彩的实际观感（2026-10-16 已因 `tickFadeMs = 500` 误判过一次）。
 */
@Composable
fun RingTimer(
    progress: Float,
    timeText: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    timeColor: Color = TextPrimary,
    /** 暂停态：整体降透明度，形成明确状态区分 */
    dimmed: Boolean = false,
    /**
     * 颜料色用的进度（0..1）。与 progress 分开：待机时几何上画满圈
     * （progress=1 作为"画布"），但颜料应显示起步色，而不是误判成"时间将尽"。
     */
    pigmentProgress: Float = progress,
    centerContent: (@Composable () -> Unit)? = null,
    /** 玻璃拟态：主按钮被按住时顶缘受光抬一档（Liquid Glass 的交互受光，只对 NOCTURNE 生效） */
    glassHighlighted: Boolean = false,
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    // 单色极简专属：2dp 细线的像素宽（环中线位置仍沿用 inset，与外主题对齐）
    val monoLinePx = with(LocalDensity.current) { MonoLineWidth.toPx() }
    // 粒子系统专属：3dp 细环 + 外缘辉光的像素宽
    val particleLinePx = with(LocalDensity.current) { ParticleLineWidth.toPx() }
    // 玻璃拟态：Apple 式单笔环的线宽
    val nocturneLinePx = with(LocalDensity.current) { NocturneLineWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)

    // ---- 主题动效参数：水彩=液态缓动（≥500ms ease-in-out，禁快速缓动）----
    // 其余主题的节奏等各自提示词到位后再单独调制。
    val mode = ActivePalette.mode
    val dimFadeMs = when (mode) {
        // 玻璃拟态禁用短促缓动（状态过渡要有"流体"感）。
        // 单色极简：Deliberate Slowness（风格规范 duration-500~700 的慢节奏）
        ThemeMode.WATERCOLOR, ThemeMode.NOCTURNE, ThemeMode.MONO -> 600
        // 粒子系统：Quantum Snap —— 提示词要求前景反馈精确快速（duration-150~200）。
        // 这条只管"控件级状态反馈"（暂停/恢复的整体降透明），跳秒数字另算（见 tickFadeMs）。
        ThemeMode.PARTICLES -> 200
        else -> 300
    }
    // 玻璃拟态的暂停/恢复走**弹簧**（Apple：状态切换用弹簧而非固定时长，天然可打断）：
    // damping 1.0 = 临界阻尼不回弹，stiffness 439 ≈ response 0.3s（(2π/0.3)²）。
    // 原来 600ms 的 tween 按下去要等 0.6 秒才暗，手感像"没点着"。
    val ringAlphaSpec: AnimationSpec<Float> = if (mode == ThemeMode.NOCTURNE) {
        spring(dampingRatio = 1f, stiffness = 439f)
    } else {
        tween(dimFadeMs, easing = FastOutSlowInEasing)
    }
    val ringAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.55f else 1f,
        animationSpec = ringAlphaSpec,
        label = "ringDim",
    )
    // 受光响应：0 = 常规 0.55，1 = 按住时 0.70（弹簧回落，不弹跳）
    val highlightBoost by animateFloatAsState(
        targetValue = if (glassHighlighted) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 632f),
        label = "glassHighlight",
    )

    // ---- 颜料色：随剩余时间流动（颜料进度 0→1 即剩余时间 1→0）----
    val pigmentBase = if (mode == ThemeMode.MONO) {
        // 单色极简：恒墨黑。一根线不随进度变色，收尾也不偏色 ——
        // 原来会把 else 分支的 WatercolorRose（粉红）引进单色主题，属色相污染。
        MonoInk
    } else if (mode == ThemeMode.PARTICLES) {
        // 粒子系统：恒冷蓝（用户选的是"单色冷蓝细环"）。收尾不引暖色 ——
        // 提示词禁暖色/大地色主调，红粉会直接破掉"深空"的冷。
        ParticleBlue
    } else {
        val pigmentP = pigmentProgress.coerceIn(0f, 1f)
        val settled = lerp(AccentGreen, Primary, (pigmentP / 0.85f).coerceIn(0f, 1f))
        val urgent = ((pigmentP - 0.88f) / 0.12f).coerceIn(0f, 1f)
        // 玻璃拟态的紧迫色是"更亮的香槟"（唯一强调色）；水彩/涂鸦用玫瑰/红笔
        val urgentColor = when (mode) {
            ThemeMode.NOCTURNE -> ChampagneBright
            else -> WatercolorRose
        }
        lerp(settled, urgentColor, urgent * 0.75f)
    }
    val pigmentLight = lerp(pigmentBase, PrimaryLight, 0.45f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // 🔴 无障碍：环是纯 Canvas，本身**没有任何语义** —— 读屏用户原来只听到中间的数字，
            //    听不出"进行到哪了"。这里把整块标成一个进度条节点（mergeDescendants 让中央文案
            //    并进同一个节点：TalkBack 一次读「75%，剩余 12:34」）。
            //    值用 clamped（1 = 满环），倒计时语义即"还剩多少"。
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        // 环区为纯静态绘制（用户要求简洁：光带 / 粒子 / 涟漪 / 流光 / 笔尖特效全部移除）：
        // 只留 底色弧 + 进度弧 + 弧首定位点 + 中央时间。
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

            // 底色弧。玻璃拟态 = **Apple 式底轨**：一整圈等宽细线（白 10%、圆头）。
            // 🔴 原来那套玻璃管（白 8% 管体 + 内外管壁 + 顶缘受光）已按"完全苹果"删除 ——
            //    Apple 的环只有两笔：一条轨道 + 一条进度，靠**宽度和明度**分层，不靠拟物光感。
            if (mode == ThemeMode.NOCTURNE) {
                drawArc(
                    color = Color.White.copy(alpha = 0.10f * ringA),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = nocturneLinePx, cap = StrokeCap.Round),
                )
            } else if (mode == ThemeMode.PARTICLES) {
                // 粒子系统（2B）：底轨 = 一根极淡冷蓝细线，整圈、圆头。
                // 只用来交代"总时长有多长"；屏幕上唯一的亮点是上面那条带外缘辉光的进度弧。
                drawArc(
                    color = ParticleBlue.copy(alpha = 0.16f * ringA),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = particleLinePx, cap = StrokeCap.Round),
                )
            } else if (mode == ThemeMode.MONO) {
                // 单色极简：底轨 = 一根极浅灰细线（#E5E5E5），整圈、平头。
                // 只用来交代"总时长有多长"，上面压的墨黑线才是进度。
                drawArc(
                    color = MonoGray200.copy(alpha = 0.9f * ringA),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = monoLinePx, cap = StrokeCap.Butt),
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
                // 主笔触（玻璃拟态走"管内液体光"、单色极简走单根细线、
                // 粒子系统走带辉光的冷蓝细环，三者都不叠这条通用粗主笔）
                if (mode != ThemeMode.NOCTURNE &&
                    mode != ThemeMode.MONO && mode != ThemeMode.PARTICLES
                ) {
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

                    // 玻璃拟态（Apple 式单笔环）：一条等宽弧、圆头收口，
                    // 颜色随剩余时间从月光蓝走到香槟（主题色保留），越紧迫越亮。
                    // 🔴 液体扫光渐变 / 管壁镜面线 / 背光暗缘 三件已删 —— 那是拟物玻璃的加法，
                    //    Apple 的环只做减法：一笔弧 + 一条底轨就是全部。
                    //    按住主按钮时整条弧微微一档变亮（Apple 的控件按下也是这个反应，不是自发循环）。
                    ThemeMode.NOCTURNE -> {
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = (0.88f + 0.12f * highlightBoost) * ringA,
                            style = Stroke(width = nocturneLinePx, cap = StrokeCap.Round),
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

                    // 单色极简：**计时就是单色的一根线**（用户 2026-10-16 定）。
                    // 2dp 墨黑细线自顶部顺时针生长，平头、无渐变、无端点圆点；
                    // 全程恒色（规范：禁一切色相 / Line Growth 细线延展）。
                    ThemeMode.MONO -> {
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = ringA,
                            style = Stroke(width = monoLinePx, cap = StrokeCap.Butt),
                        )
                    }

                    // 粒子系统（2B）：冷蓝细环 + 外缘一圈极淡辉光，精密仪器感。
                    // 不叠渐变、不加弧首能量核 —— 环上动态一律不做（2026-10-14 定的环区纯静态）。
                    ThemeMode.PARTICLES -> {
                        // 外缘辉光：同色、更宽、更淡的一道弧，托在进度弧外侧。
                        // 用单层而非多层同心弧 —— 多层硬边叠出来的亮度剖面是台阶状，放大能数出圈数。
                        val glowIns = inset - particleLinePx * 0.55f
                        drawArc(
                            color = ParticleBlue.copy(alpha = 0.20f * ringA),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(glowIns, glowIns),
                            size = Size(size.width - glowIns * 2, size.height - glowIns * 2),
                            style = Stroke(width = particleLinePx * 3.2f, cap = StrokeCap.Round),
                        )
                        // 进度细线本体
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = ringA,
                            style = Stroke(width = particleLinePx, cap = StrokeCap.Round),
                        )
                    }
                }

                // 弧首圆点（涂鸦主题补一个错位的"重描"点，像画了两笔）
                val headAngle = Math.toRadians((-90f + 360f * clamped).toDouble())
                val headCenter = Offset(
                    center.x + (radius * cos(headAngle)).toFloat(),
                    center.y + (radius * sin(headAngle)).toFloat(),
                )
                if (mode != ThemeMode.NOCTURNE && mode != ThemeMode.MONO && mode != ThemeMode.PARTICLES) {
                    // 弧首圆点。玻璃拟态不留端点（Apple 的环靠**圆头笔画**收口，不另加游标点）；
                    // 单色极简是"就是一根线"；粒子系统同理（2B 只要冷蓝细环，不要能量核）
                    drawCircle(
                        color = pigmentBase.copy(alpha = 0.75f * ringA),
                        radius = strokePx * 0.52f,
                        center = headCenter,
                    )
                    if (doodle) {
                        // 涂鸦弧首补一个错位的"重描"点，像画了两笔
                        drawCircle(
                            color = pigmentBase.copy(alpha = 0.35f * ringA),
                            radius = strokePx * 0.48f,
                            center = headCenter + Offset(strokePx * 0.07f, strokePx * 0.06f),
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 主题节奏：水彩=500ms 液态晕开（用户验收过的洇墨感）；
            // 玻璃=250ms —— 跳秒是每秒高频更新，motion.md 要求频繁交互少动效，
            // 500ms 交叉淡化会让新旧数字长时间叠印重影，250ms 一闪即过
            val tickFadeMs = when (mode) {
                // ⚠️ 死值（水彩走 ReedPaintingTimer，见文件头说明）
                ThemeMode.WATERCOLOR -> 500
                // ⚠️ 死值：玻璃拟态已改成**竖向滚动 + 弹簧**（见下面的 transitionSpec），
                //    这个 250 现在只当 else 分支的兜底值，别再拿它描述夜航的观感。
                ThemeMode.NOCTURNE -> 250
                // 单色极简：**硬切**（0 = 不做交叉淡化）。原来跟着规范抄了 500ms，
                // 但数字每秒才变一次 —— 500ms 淡入 + 500ms 淡出等于新旧数字有一半
                // 时间半透明叠印，且每次切换都打断上一次过渡，看着发虚"不干脆"。
                // 秒表数字瞬时替换最清晰。（夜航当初就是为同一个坑从 500 收到 250。）
                ThemeMode.MONO -> 0
                // 粒子系统也走下面的 120：提示词的 duration-150~200 是给**控件交互**的，
                // 跳秒数字每秒才变一次，套 200ms 就有 20% 时间新旧数字半透明叠印
                //（单色极简已因同一个坑从 500 收到硬切）。控件级节奏见 dimFadeMs。
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
                    // 单色极简与粒子系统走等宽（font-mono）
                    fontFamily = when (mode) {
                        ThemeMode.MONO -> FontFamily.Monospace
                        // 粒子系统：等宽（提示词 font-mono）—— 科技感，跳秒也不抖
                        ThemeMode.PARTICLES -> FontFamily.Monospace
                        else -> FontFamily.Serif
                    },
                    // 水彩走衬线斜体（规范的自检项点了"衬线斜体"）
                    fontStyle = if (mode == ThemeMode.WATERCOLOR) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
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
                // 🔴 玻璃拟态必须**逐位**滚动：整串 AnimatedContent(targetState = "12:34")
                //    会让 5 个字符一起滑 —— 秒数一变"整块时间"都在动。秒表只该动变化的那一位。
                if (mode == ThemeMode.NOCTURNE) {
                    RollingClockText(text = timeText, color = timeColor, width = clockWidthDp)
                } else {
                    AnimatedContent(
                        targetState = timeText,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            when {
                                // 硬切：不产生任何过渡帧 —— 数字瞬时替换（见 tickFadeMs 注释）
                                tickFadeMs == 0 ->
                                    EnterTransition.None togetherWith ExitTransition.None

                                else -> fadeIn(tween(tickFadeMs, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(tickFadeMs, easing = FastOutSlowInEasing))
                            }
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
                                fontFamily = when (mode) {
                                    ThemeMode.MONO -> FontFamily.Monospace
                                    ThemeMode.PARTICLES -> FontFamily.Monospace
                                    else -> FontFamily.Serif
                                },
                                modifier = Modifier.width(clockWidthDp),
                            )
                        }
                    }
                }
            }
            centerContent?.invoke()
        }
    }
}

/**
 * 时钟数字：默认衬线 Light + 宽字距。水彩走斜体（风格规则「文字使用衬线斜体」），
 * 单色极简与粒子系统走等宽（font-mono），其余主题正体。
 *
 * ⚠️ 曾经的"渐变文字"通路（`BlendMode.SrcIn` 遮罩）随现代渐变主题一起删除 ——
 * 那条路必须配 `CompositingStrategy.Offscreen` 才不会连带改写身后像素，历史踩过坑。
 * 以后若再要做渐变字，别忘图层隔离。
 */
@Composable
private fun ClockText(
    text: String,
    color: Color,
    italic: Boolean,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Serif,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp,
        color = color,
    )
}

/**
 * 逐位滚动的时钟数字（玻璃拟态专用）。
 *
 * 🔴 为什么必须按位拆开：`AnimatedContent(targetState = timeText)` 的 target 是**整串** "12:34"，
 *    秒数一变整串就换了 → 5 个字符一起滑，等于"整块时间"在动（2026-10-17 他一句
 *    "怎么是整个时间动"点出来的）。这里每一位一个定宽槽位、target 是**单个字符**：
 *    没变的位不产生任何过渡，只有真正变化的那一位（通常是最右位）滚动。
 *
 * 定宽槽位不是多余的：衬线数字不等宽，某位从 1 变 8 会把后面的位往右推。
 */
@Composable
private fun RollingClockText(
    text: String,
    color: Color,
    width: Dp,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // 必须与 ClockText 同款样式，否则量出来的宽度和实际绘制对不上
    val style = remember {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
        )
    }
    val digitWidth = remember(style) {
        ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }
    }
    val colonWidth = remember(style) { measurer.measure(":", style).size.width }

    Row(
        modifier = Modifier.width(width),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEach { ch ->
            val slotPx = if (ch.isDigit()) digitWidth else colonWidth
            // clipToBounds 把进出场裁在槽位内 —— 这才是"滚轮"的样子；
            // 不裁的话新数字会从下面飘出来压到模式文案上
            Box(Modifier.width(with(density) { slotPx.toDp() }).clipToBounds()) {
                if (ch.isDigit()) {
                    // 同一位上字符没变时，AnimatedContent 不会产生任何过渡帧
                    AnimatedContent(
                        targetState = ch,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            // 位移慢、透明度快：滚动放慢到 response≈0.4s（Apple「移动/画中画」档
                            // stiffness=(2π/0.4)²≈247），但淡入仍是 0.25s —— 否则数字会长时间
                            // 半透明地晃着，看着发虚。两种速度分开调才是"玻璃"该有的手感。
                            val roll = spring<IntOffset>(dampingRatio = 1f, stiffness = 247f)
                            val fade = spring<Float>(dampingRatio = 1f, stiffness = 632f)
                            (slideInVertically(roll) { it / 5 } + fadeIn(fade)) togetherWith
                                (slideOutVertically(roll) { -it / 5 } + fadeOut(fade))
                        },
                        label = "digitRoll",
                    ) { c ->
                        ClockText(text = c.toString(), color = color, italic = false)
                    }
                } else {
                    // 冒号不参与滚动，否则每次跳秒它也跟着动
                    ClockText(text = ch.toString(), color = color, italic = false)
                }
            }
        }
    }
}
