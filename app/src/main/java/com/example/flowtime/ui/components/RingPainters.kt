package com.example.flowtime.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.ImperialGold
import com.example.flowtime.ui.theme.MonoGray200
import com.example.flowtime.ui.theme.ParticleBlue
import com.example.flowtime.ui.theme.ThemeMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * 环的**绘制层**：几何 + 颜料，与布局/文案/翻牌无关。
 *
 * 2026-10-25 从 [RingTimer] 里拆出来。原因：那个文件当时 760 行，
 * 里面同时有"量尺寸 / 排数字 / 五套主题画弧 / 水彩死代码 / 一个没人调用的
 * 滚动数字组件"，修一个几何 bug 要在一堆无关代码里找。
 * 拆开后：**[RingTimer] 负责摆放，本文件负责画**，每个主题的画法是独立的命名函数。
 *
 * 规矩（改这里之前先读）：
 * - 环区是**纯静态**绘制（用户 2026-10-14 定）：不做光带 / 粒子 / 涟漪 / 流光 / 笔尖特效。
 *   所以这里只有 drawArc / drawCircle，没有任何随帧变化的量。
 * - 暂停的暗化**不用图层透明度**，而是把 alpha 乘进每个颜色（防页面切换残影）。
 * - 描边宽度由各主题自己定（6dp 的表壳 / 2dp 的单色 / 3dp 的冷蓝环…），
 *   不统一用 14dp 的 `strokeWidth` 形参 —— 见 [RingFrame.strokePx] 的注释。
 */
internal class RingFrame(
    /** 环的矩形：外接方框内缩 [inset] 后的样子 */
    val topLeft: Offset,
    val arcSize: Size,
    val center: Offset,
    val radius: Float,
    /** 整块画布尺寸（水彩/粒子的"边缘细弧"要用它反推） */
    val canvasSize: Size,
    /**
     * 通用"管"宽（14dp）：水彩补笔 / 涂鸦蜡笔 / 兜底主题用。
     * 玻璃拟态 6dp、单色 2dp、粒子 3dp 各有自己的宽度，不读这个值。
     */
    val strokePx: Float,
    val monoPx: Float,
    val particlePx: Float,
    val nocturnePx: Float,
    val nocturneTrackPx: Float,
    /** 宫廷御制专属：3dp 描金细环（金不满章 —— 环是细线，不是金镏子） */
    val imperialPx: Float,
    /** 暂停暗化系数（1 = 正常，0.55 = 暂停） */
    val ringAlpha: Float,
    /** 主按钮按住时的微提亮（只对玻璃拟态生效） */
    val highlightBoost: Float,
    val pigmentBase: Color,
    val pigmentLight: Color,
)

/** 底色弧（只交代"总时长有多长"，不代表进度） */
internal fun DrawScope.drawRingTrack(mode: ThemeMode, f: RingFrame) {
    when (mode) {
        // 玻璃拟态 = 表壳轨道：一圈发丝线、平头，线条语言与卡片的 1px 描边一致。
        // Apple 的环只有两笔：一条轨道 + 一条进度，靠**宽度和明度**分层，不靠拟物光感。
        ThemeMode.NOCTURNE -> drawArc(
            color = Color.White.copy(alpha = 0.12f * f.ringAlpha),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            style = Stroke(width = f.nocturneTrackPx, cap = StrokeCap.Butt),
        )

        // 粒子系统：极淡冷蓝细线，整圈圆头 —— 屏幕上唯一的亮点是上面那条带辉光的进度弧
        ThemeMode.PARTICLES -> drawArc(
            color = ParticleBlue.copy(alpha = 0.16f * f.ringAlpha),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            style = Stroke(width = f.particlePx, cap = StrokeCap.Round),
        )

        // 单色极简：极浅灰细线，平头 —— 上面压的墨黑线才是进度
        ThemeMode.MONO -> drawArc(
            color = MonoGray200.copy(alpha = 0.9f * f.ringAlpha),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            style = Stroke(width = f.monoPx, cap = StrokeCap.Butt),
        )

        // 宫廷御制 = 描金发丝轨道（金 10%），与深漆卡的 1px 描金边同一套线条语言
        ThemeMode.IMPERIAL -> drawArc(
            color = ImperialGold.copy(alpha = 0.10f * f.ringAlpha),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            style = Stroke(width = f.imperialPx, cap = StrokeCap.Butt),
        )

        // 兜底（涂鸦 / 以及任何走到这里的主题）
        else -> drawArc(
            color = CardGreenLight.copy(alpha = 0.7f * f.ringAlpha),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            style = Stroke(width = f.strokePx, cap = StrokeCap.Round),
        )
    }
}

/**
 * 进度弧（从 12 点顺时针生长）+ 弧首圆点。
 *
 * [clamped] 为 0 时整块不画；分段全部按"是否本主题"判断，互不叠加。
 */
internal fun DrawScope.drawRingProgress(mode: ThemeMode, f: RingFrame, clamped: Float) {
    if (clamped <= 0f) return
    val sweep = 360f * clamped
    val gradient = Brush.sweepGradient(
        colors = listOf(f.pigmentLight, f.pigmentBase, f.pigmentLight),
        center = f.center,
    )

    // 通用粗主笔：玻璃（只一笔细线）/ 单色（一根墨线）/ 粒子（辉光细环）/ 宫廷（描金细环）都不叠这一层
    if (mode != ThemeMode.NOCTURNE && mode != ThemeMode.MONO && mode != ThemeMode.PARTICLES
        && mode != ThemeMode.IMPERIAL
    ) {
        drawArc(
            brush = gradient,
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            alpha = 0.72f * f.ringAlpha,
            style = Stroke(width = f.strokePx, cap = StrokeCap.Round),
        )
    }

    when (mode) {
        // 玻璃拟态（表壳）：一条等宽弧、平头收口。明暗不用恒色 ——
        // 环和卡片受同一个顶光：上缘亮、下缘沉，这样环和翻牌卡属于同一套材质，
        // 而不是"钢卡 + 发光塑料环"。色相仍随剩余时间走到香槟，保留紧迫语义。
        ThemeMode.NOCTURNE -> drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(
                    f.pigmentLight.copy(alpha = 0.95f),
                    f.pigmentBase.copy(alpha = 0.82f),
                ),
                startY = f.topLeft.y,
                endY = f.topLeft.y + f.arcSize.height,
            ),
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            alpha = (0.92f + 0.08f * f.highlightBoost) * f.ringAlpha,
            style = Stroke(width = f.nocturnePx, cap = StrokeCap.Butt),
        )

        // 手绘涂鸦：两道短虚线叠出蜡笔颗粒（粗粝感）
        ThemeMode.DOODLE -> {
            drawArc(
                brush = gradient,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = f.topLeft, size = f.arcSize,
                alpha = 0.45f * f.ringAlpha,
                style = Stroke(
                    width = f.strokePx * 0.8f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(f.strokePx * 0.9f, f.strokePx * 0.45f),
                    ),
                ),
            )
            drawArc(
                brush = gradient,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = f.topLeft, size = f.arcSize,
                alpha = 0.35f * f.ringAlpha,
                style = Stroke(
                    width = f.strokePx * 0.5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(f.strokePx * 0.5f, f.strokePx * 0.7f),
                        f.strokePx * 0.6f,
                    ),
                ),
            )
        }

        // 单色极简：**计时就是单色的一根线**（用户 2026-10-16 定）。
        // 墨黑细线自顶部顺时针生长，平头、无渐变、无端点、全程恒色（禁一切色相）。
        ThemeMode.MONO -> drawArc(
            color = f.pigmentBase,
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            alpha = f.ringAlpha,
            style = Stroke(width = f.monoPx, cap = StrokeCap.Butt),
        )

        // 宫廷御制：一条描金细弧，上缘亮金 → 下缘鎏金（环与深漆卡吃同一束顶光），
        // 平头收口、无端点辉光 —— 金色只做章：一条细线就够，不镀金镏。
        ThemeMode.IMPERIAL -> drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(
                    f.pigmentLight.copy(alpha = 0.95f),
                    f.pigmentBase.copy(alpha = 0.82f),
                ),
                startY = f.topLeft.y,
                endY = f.topLeft.y + f.arcSize.height,
            ),
            startAngle = -90f, sweepAngle = sweep, useCenter = false,
            topLeft = f.topLeft, size = f.arcSize,
            alpha = f.ringAlpha,
            style = Stroke(width = f.imperialPx, cap = StrokeCap.Butt),
        )

        // 粒子系统：外缘一圈极淡辉光 + 本体细线，精密仪器感。
        // 辉光用**单层**而非多层同心弧 —— 多层硬边叠出来的亮度剖面是台阶状，放大能数出圈数。
        ThemeMode.PARTICLES -> {
            val glowInset = f.topLeft.x - f.particlePx * 0.55f
            drawArc(
                color = ParticleBlue.copy(alpha = 0.20f * f.ringAlpha),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(glowInset, glowInset),
                size = Size(
                    f.canvasSize.width - glowInset * 2,
                    f.canvasSize.height - glowInset * 2,
                ),
                style = Stroke(width = f.particlePx * 3.2f, cap = StrokeCap.Round),
            )
            drawArc(
                color = f.pigmentBase,
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = f.topLeft, size = f.arcSize,
                alpha = f.ringAlpha,
                style = Stroke(width = f.particlePx, cap = StrokeCap.Round),
            )
        }

        // 其余主题：只有上面那条通用主笔（水彩主题不走本组件）
        else -> Unit
    }

    // 弧首圆点：玻璃不留端点（Apple 靠圆头笔画收口，不另加游标点）、
    // 单色是"就是一根线"、粒子只要冷蓝细环、宫廷只要一条描金细弧 —— 四者都不画。
    if (mode != ThemeMode.NOCTURNE && mode != ThemeMode.MONO && mode != ThemeMode.PARTICLES
        && mode != ThemeMode.IMPERIAL
    ) {
        val headAngle = Math.toRadians((-90f + 360f * clamped).toDouble())
        val headCenter = Offset(
            f.center.x + (f.radius * cos(headAngle)).toFloat(),
            f.center.y + (f.radius * sin(headAngle)).toFloat(),
        )
        drawCircle(
            color = f.pigmentBase.copy(alpha = 0.75f * f.ringAlpha),
            radius = f.strokePx * 0.52f,
            center = headCenter,
        )
        if (mode == ThemeMode.DOODLE) {
            // 涂鸦弧首补一个错位的"重描"点，像画了两笔
            drawCircle(
                color = f.pigmentBase.copy(alpha = 0.35f * f.ringAlpha),
                radius = f.strokePx * 0.48f,
                center = headCenter + Offset(f.strokePx * 0.07f, f.strokePx * 0.06f),
            )
        }
    }
}
