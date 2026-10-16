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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.BorderColor
import com.example.flowtime.ui.theme.GlassBottomShade
import com.example.flowtime.ui.theme.GlassInnerLight
import com.example.flowtime.ui.theme.ParticleBlue
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
 * - 现代渐变：白 10% 玻璃卡 + 白 20% 细边 + 顶边受光高光，大圆角
 *   （**不用 elevation 阴影**：阴影会从半透明卡内侧透出、在卡内形成暗带，详见下方分支注释）；
 * - 粒子系统：深色玻璃卡（#0f1419 @70%）+ 白 10% 细边 + 顶边受光高光 + 冷蓝星云外辉光
 *   （同样**不用 elevation 阴影**，外发光走手绘径向渐变，见下方分支注释）。
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
        ThemeMode.GRADIENT -> {
            // 现代渐变：白 10% 玻璃卡 + 白 20% 细边 + 顶边受光高光 + 紫彩色光晕阴影。
            // Android 端没有逐卡实时模糊，但页面底是"有色的光球场景"——半透明卡正好
            // 借到身后那团渐变色，观感就是"渐变玻璃"（对着纯色底才是死板色块）。
            // 与夜航玻璃的区别：夜航内容卡刻意用近实心钢蓝保可读；本主题卡片透，
            // 因为底是深紫、白 10% 叠上去仍是深色，白字对比度 >10:1，可读性有余量。
            // 🔴 本主题刻意**不加 Modifier.shadow（elevation 高度阴影）**：
            //    Android 的高度阴影是一整块「模糊剪影」，且会被 clipToOutline 裁进轮廓**内侧**——
            //    半透明卡（白 10%）挡不住它，于是卡内出现一圈「外亮内暗」的暗带
            //    （实测：卡内暗约 7%，暗带自边缘向内约 20dp，正好等于 elevation 的量级），
            //    而卡外本该有的光晕反被裁掉、一点都看不见 —— 即该阴影在此只有害无益。
            //    "发光"感改由下面的受光高光 + 白描边承担，不走 elevation。
            Box(modifier.clip(shape)) {
                // 第 1 层：白玻璃底（白 10%；弹窗传入更实的容器色）
                Box(Modifier.matchParentSize().background(color))
                // 第 2 层：顶边受光高光（白 12% → 全透明，光从上方来）
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x1FFFFFFF), Color(0x00FFFFFF)),
                        ),
                    ),
                )
                // 第 3 层：1dp 白 20% 边框（规范 border-white/20）
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
            // 粒子系统：深空玻璃卡 —— #0f1419 @70% 底 + 白 10% 细边 + 顶边受光高光，
            // 外缘一圈冷蓝"星云"辉光（提示词 Nebula Glow：低不透明度、大范围扩散）。
            // 🔴 外发光走**手绘径向渐变**，不用 Modifier.shadow —— Android 的高度阴影是一整块
            //    模糊剪影、且会被 clipToOutline 裁进轮廓**内侧**，半透明卡挡不住它，卡内会出现
            //    "外亮内暗"的暗带（现代渐变已实测踩过，见下面 GRADIENT 分支的完整记录）。
            //    这里把光晕画在 `clip` **左侧**（Modifier 链上在裁剪之外），所以它不被卡片轮廓裁掉；
            //    卡内也会透出一点冷蓝 —— 正是"玻璃借身后光"的效果，而不是一块死板的实色。
            Box(
                modifier = modifier
                    .drawBehind {
                        val c = Offset(size.width / 2f, size.height * 0.32f)
                        val r = size.maxDimension * 0.92f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ParticleBlue.copy(alpha = 0.18f),
                                    // 终止色用"同色 alpha=0"，别用 Color.Transparent（会泛灰边）
                                    ParticleBlue.copy(alpha = 0f),
                                ),
                                center = c,
                                radius = r,
                            ),
                            radius = r,
                            center = c,
                        )
                    }
                    .clip(shape),
            ) {
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
