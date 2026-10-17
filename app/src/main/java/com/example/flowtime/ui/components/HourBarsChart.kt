package com.example.flowtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextSecondary

/**
 * 24 小时时段分布柱状图（水彩画风）。
 * 柱高按「该小时分钟 / 峰值」归一化（峰值不足 30 分钟时按 30 计，避免小数据全平），
 * 最专注的时段用主色高亮，其余为半透明青绿；横轴仅标 0 / 6 / 12 / 18 / 23，逐根生长入场。
 */
@Composable
fun HourBarsChart(
    hourMinutes: List<Int>,
    modifier: Modifier = Modifier,
    startDelayMillis: Int = 0,
) {
    if (hourMinutes.all { it <= 0 }) {
        Text("暂无记录，先去完成一次专注吧", fontSize = 13.sp, color = TextPlaceholder)
        return
    }
    val peakHour = hourMinutes.indices.maxByOrNull { hourMinutes[it] } ?: 0
    val maxMinutes = maxOf(hourMinutes.maxOrNull() ?: 0, 30)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        hourMinutes.forEachIndexed { hour, minutes ->
            // 动画对象是"归一化柱高"本身：首次从 0 长出（逐根错峰），切范围时从当前高度走过去
            val fill = rememberGrowingFloat(
                target = minutes.toFloat() / maxMinutes,
                enterDelayMillis = startDelayMillis + 60 + hour * 20,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((3 + 96f * fill).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (hour == peakHour) Primary else AccentGreen.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hour % 6 == 0 || hour == 23) hour.toString() else "",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
