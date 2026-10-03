package com.example.flowtime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.Dimens

import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.WatercolorInk

/**
 * 水彩柔和阴影：低透明度蓝灰向外大幅扩散（模拟颜料在湿纸上晕开），
 * 替代生硬的黑色投影，禁止使用硬边阴影。
 *
 * 只用在「实心卡片 / 不透明底」的元素上（如 ProCard）。
 * 不要用在按钮或透明底元素上，原因有两条（都实测过）：
 * 1. 透明底时阴影轮廓会退化成矩形，隔着透明底就是一块方角色斑；
 * 2. 页面转场是整屏淡入，阴影在图层的首帧才生效，会看到一下闪频。
 * Material 3 里填充按钮本身也是 0 高度、不带阴影的。
 */
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp = 8.dp,
    color: Color = WatercolorInk,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = color.copy(alpha = 0.35f),
    spotColor = color.copy(alpha = 0.28f),
)

/**
 * 水彩卡片：大圆角 + 细描边（蓝灰 20%）+ 柔和扩散阴影 + 纸张色底。
 * 禁止直角、禁止硬边框、禁止零层级的纯白方块。
 */
@Composable
fun ProCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.CardRadius),
    color: Color = SurfaceCard,
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.softShadow(shape, elevation),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 0.dp,
        content = content,
    )
}
