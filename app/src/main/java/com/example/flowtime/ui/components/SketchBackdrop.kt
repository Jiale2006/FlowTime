package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.SketchGraphite
import com.example.flowtime.ui.theme.SketchPaper

/**
 * 铅笔手绘场景底（素描本一页）：
 * 纸张米色底 + 极淡的铅笔点阵纸纹（每 16dp 一个 5% 铅笔灰圆点，隔行错位）。
 *
 * 有意做得极淡（规范要求背景不得抢内容）：凑近才看得见，远看就是一片暖纸色。
 * 全部静态绘制、无状态读取 → 只在重组时画一次，天然满足 reduced-motion。
 */
@Composable
fun SketchBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(SketchPaper)) {
        Canvas(Modifier.matchParentSize()) {
            val gap = 16.dp.toPx()
            val radius = 0.7.dp.toPx()
            val dot = SketchGraphite.copy(alpha = 0.05f)
            // 错位点阵：奇数行右移半个间距，近似素描纸的颗粒排布（整齐网格会显"数字感"）
            val points = ArrayList<Offset>()
            var y = gap / 2f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) gap / 2f else gap
                while (x < size.width) {
                    points += Offset(x, y)
                    x += gap
                }
                y += gap
                row++
            }
            // 一次 drawPoints 画完全部点（避免上千次 drawCircle）
            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = dot,
                strokeWidth = radius * 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}
