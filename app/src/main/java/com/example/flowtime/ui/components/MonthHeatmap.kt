package com.example.flowtime.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.CardCream
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPlaceholder
import com.example.flowtime.ui.theme.TextSecondary

private val WeekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 色阶图例（由浅到深，与 heatColor 一一对应） */
private val HeatLegend = listOf(
    CardCream,
    AccentGreen.copy(alpha = 0.35f),
    AccentGreen.copy(alpha = 0.55f),
    PrimaryLight.copy(alpha = 0.6f),
    Primary.copy(alpha = 0.75f),
)

/**
 * 近 30 天热力图（水彩画风）：7 列（一~日）× 5 行圆角格子，格子颜色深浅代表当日专注时长。
 * 范围外的日子淡显（聚焦当前所选范围），未来日期落回纸色；整块淡入入场。
 */
@Composable
fun MonthHeatmap(
    cells: List<AppViewModel.HeatCell>,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val reveal by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "heatReveal",
    )

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekLabels.forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { cell ->
                    val fill = heatColor(cell)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            // 入场动画必须作用在颜色 alpha 上：Modifier.alpha() 放在 .background() 之后
                            // 会把整块（含背景）画成不可见，实测热力图会整片空白。
                            .background(fill.copy(alpha = (fill.alpha * reveal).coerceIn(0f, 1f))),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("少", fontSize = 10.sp, color = TextPlaceholder)
            HeatLegend.forEach { color ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            Text("多", fontSize = 10.sp, color = TextPlaceholder)
        }
    }
}

/** 单格颜色：5 档半透明水彩色阶；范围外淡显 */
private fun heatColor(cell: AppViewModel.HeatCell): Color {
    val base = when {
        cell.minutes <= 0 -> CardCream
        cell.minutes < 30 -> AccentGreen.copy(alpha = 0.35f)
        cell.minutes < 60 -> AccentGreen.copy(alpha = 0.55f)
        cell.minutes < 90 -> PrimaryLight.copy(alpha = 0.6f)
        else -> Primary.copy(alpha = 0.75f)
    }
    // 范围外整体轻微淡显：既聚焦当前范围，又保留近 30 天坚持情况的可读性
    return if (cell.inRange) base else base.copy(alpha = base.alpha * 0.7f)
}
