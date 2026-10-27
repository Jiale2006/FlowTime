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
 *   无 blur 阴影、无渐变、小圆角；
 * - 粒子系统：深色玻璃卡（#0f1419 @70%）+ 白 10% 细边 + 顶边受光高光，**无卡片级辉光**
 *   （曾有一圈冷蓝外发光，叠加成整屏蓝雾，已按用户要求删除，见下方分支注释）。
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
        ThemeMode.PARTICLES -> {
            // 粒子系统：深空玻璃卡 —— #0f1419 @70% 底 + 白 10% 细边 + 顶边受光高光。
            // 🔴 刻意**不加任何卡片级辉光**。原来这里有一圈冷蓝"星云"外发光（提示词 Nebula Glow）：
            //    半径 = 卡片最长边 × 0.92、且画在 `clip` 左侧所以会溢到卡外。统计页卡片一多，
            //    每张光晕半径接近整张卡宽、彼此叠加 → 整屏泛蓝雾。用户实测反馈"像一层蓝色幕布
            //    压在数据上"、"过了几帧又有了"（卡片是 Room 数据加载后才渲染的，光晕随之出现）。
            //    实测：屏最左边缘（那里没有卡片）蓝色 27.5 → 59.5（×2.2）；全屏蓝色富余度
            //    13.9 → 33.4。"星云感"改由背景的粒子网承担 —— 卡片只做玻璃材质，不再自己发光。
            //    （同样不走 Modifier.shadow：高度阴影会被 clipToOutline 裁进轮廓内侧，
            //      半透明卡上会出现卡内暗带，现代渐变主题曾实测坐实。）
            Box(modifier.clip(shape)) {
                // 第 1 层：深色玻璃底（#0f1419 @70%；弹窗传入更实的容器色）
                Box(Modifier.matchParentSize().background(color))
                // 第 2 层：顶边受光高光（白 8% → 全透明，光从上方来）
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x14FFFFFF), Color(0x00FFFFFF)),
                        ),
                    ),
                )
                // 第 3 层：1dp 白 10% 边框（提示词 border-white/5 或 /10）
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = shape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, BorderColor),
                ) {}
                Box { content() }
            }
        }
        ThemeMode.IMPERIAL -> {
            // 宫廷御制：深漆实体卡（#14211F @90%，禁 backdrop-blur）+ 描金发丝边
            // + 方向性光：顶边受光高光 / 底边背光暗缘 / 外层深影（光才有方向）。
            // 刻意不加回纹角：卡片数量多时逐卡四角描线会抢戏，提示词也只是"可选"。
            Box(modifier.softShadow(shape, 12.dp).clip(shape)) {
                Box(Modifier.matchParentSize().background(color))
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x1AF0E6D2), Color.Transparent),
                        ),
                    ),
                )
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.97f to Color.Transparent,
                                1f to Color(0x73020505),
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
        ThemeMode.WATERCOLOR, ThemeMode.MONO -> {
            // 浅色纸面通用材质：实心底 + 细描边 + 扩散阴影。
            // 阴影强度由各自调色板的 shadowAmbient/SpotAlpha 决定
            // （水彩柔和、单色/水墨几乎不可见）。
            // 🔴 原来 MONO 不在任何分支里（when 无 else）→ 单色主题下卡片整个不渲染，已修。
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
