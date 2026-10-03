package com.example.flowtime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.CardCream
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.TextSecondary
import com.example.flowtime.ui.theme.WatercolorInk

/**
 * 大类标注：让每个小任务都能看出属于哪个大类。
 * 水彩规则：低透明度底 + 大圆角 + 无硬边框；未分类用纸色弱化显示。
 */
@Composable
fun CategoryBadge(
    categoryName: String?,
    modifier: Modifier = Modifier,
    /** 深色卡片上用：以当前卡片文字色渲染 */
    onCardColor: androidx.compose.ui.graphics.Color? = null,
) {
    val isCategorized = categoryName != null
    val label = categoryName ?: "未分类"
    val bg = when {
        onCardColor != null -> onCardColor.copy(alpha = 0.16f)
        isCategorized -> WatercolorInk.copy(alpha = 0.14f)
        else -> CardCream
    }
    val fg = when {
        onCardColor != null -> onCardColor.copy(alpha = 0.9f)
        isCategorized -> Primary
        else -> TextSecondary
    }

    Text(
        text = label,
        fontSize = 11.sp,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
