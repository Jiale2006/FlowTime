package com.example.flowtime.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.DoodleInk
import com.example.flowtime.ui.theme.DoodleTeal
import com.example.flowtime.ui.theme.DoodleYellow
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 主题自适应描边：手绘涂鸦主题画虚线（border-dashed 手绘语义），
 * 其余主题画实线。颜色/宽度由调用方给，虚线开关跟随当前调色板。
 */
fun Modifier.sketchStroke(shape: Shape, color: Color, width: Dp = 1.dp): Modifier = drawBehind {
    val dashed = ActivePalette.dashedBorders
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = if (dashed) {
            PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
        } else null,
    )
    drawOutline(outline, color = color, style = stroke)
}

/**
 * 涂鸦卡片本体绘制（DrawScope 内）：
 * 1. 蓝绿标记笔「硬偏移阴影」——整体平移 (3dp,3dp) 的实心轮廓，无 blur，
 *    像用马克笔在纸下又描了一遍；
 * 2. 奶白纸面填充；
 * 3. 墨黑虚线描边（手绘笔触）；
 * 4. 顶部一条微旋转的黄胶带，压住纸角。
 */
fun DrawScope.drawDoodleCard(shape: Shape, surfaceColor: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val offsetPx = 3.dp.toPx()
    // 1. 硬偏移阴影（蓝绿标记笔）
    translate(offsetPx, offsetPx) {
        drawOutline(outline, color = DoodleTeal)
    }
    // 2. 纸面
    drawOutline(outline, color = surfaceColor)
    // 3. 墨黑虚线描边
    drawOutline(
        outline,
        color = DoodleInk,
        style = Stroke(
            width = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
        ),
    )
    // 4. 黄胶带：顶部居中、微旋转，压在纸边上看起像贴上去的
    val tapeW = 46.dp.toPx()
    val tapeH = 13.dp.toPx()
    val pivot = Offset(size.width / 2f, 0f)
    rotate(degrees = -3f, pivot = pivot) {
        drawRect(
            color = DoodleYellow.copy(alpha = 0.75f),
            topLeft = Offset(pivot.x - tapeW / 2f, -tapeH / 2f + 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(tapeW, tapeH),
        )
    }
}

/**
 * 铅笔手绘卡片本体绘制（DrawScope 内）：
 * 1. 交叉排线阴影 —— 卡片轮廓向右下平移出一份「影子」，只在那份影子里画两组 45° 铅笔线
 *    （规范「阴影使用交叉线条效果而非纯色」）；
 * 2. 纸面填充；
 * 3. 铅笔灰虚线描边（手绘笔触）。
 */
fun DrawScope.drawSketchCard(shape: Shape, surfaceColor: Color, graphite: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutlinePath(outline) }
    val offsetPx = 3.dp.toPx()
    val lineGap = 6.dp.toPx()
    val lineWidth = 0.9.dp.toPx()
    val hatch = graphite.copy(alpha = 0.28f)
    val yTop = -offsetPx
    val yBottom = size.height + offsetPx
    val vSpan = yBottom - yTop

    // 1. 交叉排线阴影：影子区域裁剪 + 两组对角铅笔线
    val shadowPath = Path().apply { addPath(path, Offset(offsetPx, offsetPx)) }
    clipPath(shadowPath) {
        var x = -vSpan
        while (x < size.width + vSpan) {
            drawLine(hatch, Offset(x, yBottom), Offset(x + vSpan, yTop), strokeWidth = lineWidth)
            x += lineGap
        }
        var x2 = -vSpan
        while (x2 < size.width + vSpan) {
            drawLine(hatch, Offset(x2, yTop), Offset(x2 + vSpan, yBottom), strokeWidth = lineWidth)
            x2 += lineGap
        }
    }
    // 2. 纸面
    drawOutline(outline, color = surfaceColor)
    // 3. 铅笔灰虚线描边（手绘笔触）
    drawOutline(
        outline,
        color = graphite,
        style = Stroke(
            width = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
        ),
    )
}

/** Outline → Path：本版本 Compose 没有 Outline.asPath()，按类型分派自己填 */
private fun Path.addOutlinePath(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/** 涂鸦装饰小星星（五角星描边），场景背景用 */
fun DrawScope.drawDoodleStar(center: Offset, radius: Float, color: Color, strokeWidth: Float) {
    val path = Path()
    val inner = radius * 0.42f
    for (i in 0 until 10) {
        val angle = Math.toRadians((-90.0 + i * 36.0))
        val r = if (i % 2 == 0) radius else inner
        val point = Offset(
            center.x + (r * kotlin.math.cos(angle)).toFloat(),
            center.y + (r * kotlin.math.sin(angle)).toFloat(),
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color = color, style = Stroke(width = strokeWidth))
}

/** 涂鸦波浪线（随手画的下划线），场景背景用 */
fun DrawScope.drawDoodleSquiggle(start: Offset, width: Float, amplitude: Float, color: Color, strokeWidth: Float) {
    val path = Path()
    path.moveTo(start.x, start.y)
    val segment = width / 5f
    var x = start.x
    var y = start.y
    repeat(5) { i ->
        val midX = x + segment / 2f
        val midY = y + if (i % 2 == 0) -amplitude else amplitude
        x += segment
        path.quadraticBezierTo(midX, midY, x, y)
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}
