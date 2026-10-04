package com.example.flowtime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.GlassBottomShade
import com.example.flowtime.ui.theme.GlassInnerLight
import com.example.flowtime.ui.theme.SurfaceCard
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 卡片阴影：墨色与透明度随主题走（水彩=蓝灰颜料晕染，夜航=近黑深影）。
 * 涂鸦主题不用 blur 阴影（走 ProCard 内的硬偏移描边），不会走到这里。
 *
 * 只用在「实心卡片 / 不透明底」的元素上。不要用在按钮或透明底元素上，
 * 原因有两条（都实测过）：
 * 1. 透明底时阴影轮廓会退化成矩形，隔着透明底就是一块方角色斑；
 * 2. 页面转场是整屏淡入，阴影在图层的首帧才生效，会看到一下闪频。
 */
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp = 8.dp,
    color: Color = ActivePalette.shadowInk,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = color.copy(alpha = ActivePalette.shadowAmbientAlpha),
    spotColor = color.copy(alpha = ActivePalette.shadowSpotAlpha),
)

/**
 * 卡片容器，材质随主题切换：
 * - 水彩画风：纸张色实心底 + 细描边（蓝灰 20%）+ 柔和扩散阴影；
 * - 玻璃拟态：无色玻璃（白 8%）+ 细描边（白 15%）+ 三层方向性光
 *   （顶边受光高光、底边背光暗缘、外层近黑深阴影）；
 * - 手绘涂鸦：奶白纸面 + 墨黑虚线描边 + 蓝绿标记笔硬偏移阴影 + 黄胶带，
 *   无 blur 阴影、无渐变、小圆角。
 */
@Composable
fun ProCard(
    modifier: Modifier = Modifier,
    shape: Shape = ActivePalette.cardShape,
    color: Color = SurfaceCard,
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val palette = ActivePalette
    when (palette.mode) {
        ThemeMode.DOODLE -> {
            // 硬偏移阴影会画出布局边界（右/下各 3dp），所以不 clip
            Box(modifier.drawBehind { drawDoodleCard(shape, color) }) {
                Box { content() }
            }
        }
        ThemeMode.NOCTURNE -> {
            Box(modifier.softShadow(shape, 12.dp).clip(shape)) {
                // 第 1 层：无色玻璃底（默认 GlassFill；弹窗等可传入更实的容器色）
                Box(Modifier.matchParentSize().background(color))
                // 第 2 层：内发光——顶边受光高光，向下渐隐（光从上方来）
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colors = listOf(GlassInnerLight, Color.Transparent),
                        ),
                    ),
                )
                // 第 3 层：底边背光暗缘（最后 ~3% 高度的深色渐入）
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.96f to Color.Transparent,
                                1f to GlassBottomShade,
                            ),
                        ),
                    ),
                )
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = shape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, BorderColor),
                ) {}
                Box { content() }
            }
        }
        ThemeMode.WATERCOLOR -> {
            Surface(
                modifier = modifier.softShadow(shape, elevation),
                shape = shape,
                color = color,
                border = BorderStroke(1.dp, BorderColor),
                shadowElevation = 0.dp,
                content = content,
            )
        }
    }
}

/** 旧默认值保留：卡片圆角原来走 Dimens.CardRadius（水彩/夜航分别 20/24） */
