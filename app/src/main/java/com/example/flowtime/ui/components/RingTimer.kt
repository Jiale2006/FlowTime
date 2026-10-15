package com.example.flowtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.example.flowtime.ui.theme.GlassBorder
import com.example.flowtime.ui.theme.GlassBorderHigh
import com.example.flowtime.ui.theme.GlassBottomShade
import com.example.flowtime.ui.theme.GlassFillHigh
import com.example.flowtime.ui.theme.InkNong
import com.example.flowtime.ui.theme.InkQing
import com.example.flowtime.ui.theme.InkYanzhi
import com.example.flowtime.ui.theme.Moonlight
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.ThemeMode
import com.example.flowtime.ui.theme.WatercolorInk
import com.example.flowtime.ui.theme.WatercolorRose
import kotlin.math.cos
import kotlin.math.sin

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
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)

    // ---- 主题动效参数：水彩=液态缓动（≥500ms ease-in-out，禁快速缓动）----
    // 其余主题的节奏等各自提示词到位后再单独调制。
    val mode = ActivePalette.mode
    val dimFadeMs = when (mode) {
        // 玻璃拟态禁 duration-100/150：状态淡入淡出一律 500ms 流体缓动
        // 水墨：慢过渡模拟墨色晕染（风格规范 duration-700 的移植档）
        ThemeMode.WATERCOLOR, ThemeMode.NOCTURNE, ThemeMode.INKWASH -> 600
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
        if (mode == ThemeMode.INKWASH) {
            // 墨分五色：起步清墨，随时间推移墨色渐浓（时间越少墨越重）；
            // 紧迫时洇开一点胭脂（印章红降饱和，禁艳色）
            val inkSettled = lerp(InkQing, InkNong, pigmentP)
            val urgent = ((pigmentP - 0.88f) / 0.12f).coerceIn(0f, 1f)
            lerp(inkSettled, InkYanzhi, urgent * 0.7f)
        } else {
            val settled = lerp(AccentGreen, Primary, (pigmentP / 0.85f).coerceIn(0f, 1f))
            val urgent = ((pigmentP - 0.88f) / 0.12f).coerceIn(0f, 1f)
            // 玻璃拟态的紧迫色是"更亮的香槟"（唯一强调色），水彩/涂鸦用玫瑰/红笔
            val urgentColor = if (mode == ThemeMode.NOCTURNE) ChampagneBright else WatercolorRose
            lerp(settled, urgentColor, urgent * 0.75f)
        }
    }
    val pigmentLight = lerp(pigmentBase, PrimaryLight, 0.45f)

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
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
            } else if (mode == ThemeMode.INKWASH) {
                // 淡墨底环：清墨一圈，像下笔前蘸清水描的底稿（水墨线条纤细，0.85×）
                drawArc(
                    color = InkQing.copy(alpha = 0.32f * ringA),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx * 0.85f, cap = StrokeCap.Round),
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
                        // 背光暗缘：液体下缘一线近黑（inset bottom shade 的弧形版）。
                        // alpha 收得很低：叠在香槟液体上会发棕褐"泥圈"，月光态点到为止
                        val shadeIns = inset + strokePx * 0.24f
                        drawArc(
                            color = GlassBottomShade,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(shadeIns, shadeIns),
                            size = Size(size.width - shadeIns * 2, size.height - shadeIns * 2),
                            alpha = 0.22f * ringA,
                            style = Stroke(width = strokePx * 0.12f, cap = StrokeCap.Round),
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

                    // 水墨：飞白（干笔擦痕，长虚线细弧）+ 内缘渗墨（墨在纸纤维里洇出的淡影）
                    ThemeMode.INKWASH -> {
                        drawArc(
                            brush = gradient,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            alpha = 0.30f * ringA,
                            style = Stroke(
                                width = strokePx * 0.42f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(strokePx * 2.8f, strokePx * 1.1f),
                                    strokePx * 0.9f,
                                ),
                            ),
                        )
                        val bleedIns = inset + strokePx * 0.34f
                        drawArc(
                            color = pigmentBase,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(bleedIns, bleedIns),
                            size = Size(size.width - bleedIns * 2, size.height - bleedIns * 2),
                            alpha = 0.16f * ringA,
                            style = Stroke(width = strokePx * 0.22f, cap = StrokeCap.Round),
                        )
                    }

                    // 单色极简无专属材质：通用主笔触已够（灰阶 gradient 由调色板提供）
                    else -> {}
                }

                // 弧首圆点（涂鸦主题补一个错位的"重描"点，像画了两笔）
                val headAngle = Math.toRadians((-90f + 360f * clamped).toDouble())
                val headCenter = Offset(
                    center.x + (radius * cos(headAngle)).toFloat(),
                    center.y + (radius * sin(headAngle)).toFloat(),
                )
                if (mode == ThemeMode.NOCTURNE) {
                    // 液面前缘 = 香槟金游标（全环唯一香槟金定点）：一圈细环 + 亮核，静态不动。
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
                } else if (mode == ThemeMode.INKWASH) {
                    // 弧首 = 一滴浓墨：淡墨湿边先洇开，浓墨落点居中（静态，无动画）
                    drawCircle(
                        color = InkQing.copy(alpha = 0.30f * ringA),
                        radius = strokePx * 0.72f,
                        center = headCenter,
                    )
                    drawCircle(
                        color = pigmentBase.copy(alpha = 0.92f * ringA),
                        radius = strokePx * 0.46f,
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
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 主题节奏：水彩=500ms 液态晕开（用户验收过的洇墨感）；
            // 玻璃=250ms —— 跳秒是每秒高频更新，motion.md 要求频繁交互少动效，
            // 500ms 交叉淡化会让新旧数字长时间叠印重影，250ms 一闪即过
            val tickFadeMs = when (mode) {
                ThemeMode.WATERCOLOR -> 500
                ThemeMode.NOCTURNE -> 250
                ThemeMode.INKWASH -> 400
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
