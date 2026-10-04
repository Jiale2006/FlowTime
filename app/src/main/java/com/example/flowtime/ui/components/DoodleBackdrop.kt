package com.example.flowtime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.DoodlePaper
import com.example.flowtime.ui.theme.DoodleRed
import com.example.flowtime.ui.theme.DoodleTeal
import com.example.flowtime.ui.theme.DoodleYellow

/**
 * 手绘涂鸦场景底（笔记本一页）：
 * 奶白纸张 + 蓝色横线 + 左侧红色页边线，
 * 角落散一点涂鸦——红星星、蓝绿波浪线、咖啡渍圆环。
 * 全部静态 → 满足 reduced-motion；横线极淡不干扰正文。
 */
@Composable
fun DoodleBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(DoodlePaper)) {
        Canvas(Modifier.matchParentSize()) {
            // 笔记本横线：淡蓝、每 30dp 一条
            val lineGap = 30.dp.toPx()
            val lineColor = Color(0x2E8FB8DC)
            var y = lineGap * 2f
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += lineGap
            }
            // 红色页边线：距左边约一个字符位
            val marginX = 26.dp.toPx()
            drawLine(
                color = DoodleRed.copy(alpha = 0.40f),
                start = Offset(marginX, 0f),
                end = Offset(marginX, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
            // 咖啡渍：右下角一圈圆环
            drawCircle(
                color = Color(0x338B6F47),
                radius = 34.dp.toPx(),
                center = Offset(size.width * 0.88f, size.height * 0.84f),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            drawCircle(
                color = Color(0x228B6F47),
                radius = 40.dp.toPx(),
                center = Offset(size.width * 0.88f, size.height * 0.84f),
                style = Stroke(width = 1.dp.toPx()),
            )
            // 涂鸦：右上红星星 + 小黄星 + 左下蓝绿波浪线
            drawDoodleStar(
                center = Offset(size.width * 0.90f, size.height * 0.10f),
                radius = 13.dp.toPx(),
                color = DoodleRed.copy(alpha = 0.55f),
                strokeWidth = 2.dp.toPx(),
            )
            drawDoodleStar(
                center = Offset(size.width * 0.79f, size.height * 0.16f),
                radius = 6.dp.toPx(),
                color = DoodleYellow.copy(alpha = 0.85f),
                strokeWidth = 1.8f,
            )
            drawDoodleSquiggle(
                start = Offset(size.width * 0.06f, size.height * 0.955f),
                width = size.width * 0.22f,
                amplitude = 5.dp.toPx(),
                color = DoodleTeal.copy(alpha = 0.65f),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}
