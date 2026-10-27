package com.example.flowtime.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.GlassBorderHigh
import com.example.flowtime.ui.theme.GlassBottomShade
import com.example.flowtime.ui.theme.GlassInnerLight
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.ThemeMode
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 机械翻牌时钟（老式 split-flap）。
 *
 * 只做**显示**，不绘制任何外层背景：卡片自有卡面，外面那层交给主题
 * （夜航主题把它摆在环心，所以这里绝不能画整块底色/渐变去"帮忙"）。
 *
 * 几何（2026-10-24 重做）：**整张卡只画一次，两个半页只做裁切**。
 * 之前用"半高容器 + requiredHeight(2h) + offset"让每个半页各自测量整卡，
 * 实测上下两半不是等分（上 103px、下 51px），数字下半截被切。
 * 现在同一套绘制坐标（整卡 + 文字中心在铰链）分别裁到 0..h / h..2h，
 * 两半在几何上不可能再错位；翻页片也用同一套坐标，只是外面包一层 3D 旋转。
 *
 * 一页的动画（p: 0→1），真实翻牌机的**一页一翻到底**：
 * - 铰链 = 卡片分割线（页片自身底边），同一张页片 0°→180° 一翻到底
 * - p∈[0, .5]：页片正面（旧上半）朝观众倒下，露出后面的新上半
 * - p∈[.5, 1]：翻过 90° 后看到页片背面（新下半，绘制时绕中轴做垂直镜像，
 *   页片转到 180° 时净旋转归零），继续下摆落定，盖住旧下半
 * - 变换与明暗全部在绘制/图层阶段读 [Animatable] 的值，**不触发每帧重组**
 *
 * 颜色不写死：卡面/墨色/铰链/玻璃光边全部由 [FlipClockColors] 传入，
 * 默认值从 [ActivePalette] 派生，外部主题可整组覆盖（见 [FlipClockDefaults]）。
 */

/**
 * 翻牌卡面的主题色。
 *
 * 夜航的主题规则是"玻璃无色、光有方向"：默认值用主题的玻璃 token 做顶光/底暗缘，
 * 卡面本体取 `surfaceCard` 深钢底（近实心，保证环心数字可读），而不是白 8% 的
 * 半透明块 —— Android 没有逐卡 backdrop blur，那样会退化成"实心假玻璃色块"。
 */
@Immutable
data class FlipClockColors(
    /** 卡面上缘（受光面） */
    val faceTop: Color,
    /** 卡面下缘（背光面） */
    val faceBottom: Color,
    /** 数字墨色 */
    val ink: Color,
    /** 铰链凹槽的亮边 */
    val hinge: Color,
    /** 玻璃描边 */
    val border: Color,
    /** 玻璃顶部受光 */
    val faceLight: Color,
    /** 玻璃底部背光暗缘 */
    val faceShade: Color,
    /** 翻页时投下的阴影（同时用于铰链凹槽的暗边） */
    val flapShade: Color,
    /** 翻页背面落地前的自由边反光 */
    val flapSpecular: Color,
)

object FlipClockDefaults {
    /**
     * 翻页时长。420 太慢；280 是上一轮值。现在改用更果断的 [Motion.EaseFlip]，
     * 300ms 落地更完整；一次翻页只占 1s 跳秒的 30%，其余时间数字是静的。
     */
    const val DurationMs = 300

    /**
     * 从当前主题派生卡面色。夜航直接用主题的玻璃 token；
     * 其余主题没有玻璃层，就用墨色/阴影色按低透明度模拟，保证组件可独立复用。
     */
    @Composable
    fun colors(): FlipClockColors {
        val palette = ActivePalette
        return remember(palette) {
            val glass = palette.mode == ThemeMode.NOCTURNE
            // 玻璃卡面要比夜景底**亮一层**（参考图里的卡片也是从背景里浮起来的）。
            // surfaceCard 是"内容卡标准材质"，直接铺会形成比背景更暗的凹槽；
            // 这里用同色 + 白 10% 提亮，并强制不透明 —— 翻页片必须是实体纸片，
            // 否则静止层数字会从背面透出来。
            val faceTop = lerp(palette.surfaceCard, Color.White, 0.10f).copy(alpha = 1f)
            FlipClockColors(
                faceTop = faceTop,
                faceBottom = lerp(faceTop, palette.shadowInk, 0.18f),
                ink = palette.textPrimary,
                hinge = palette.dividerColor,
                border = palette.borderColor,
                faceLight = if (glass) {
                    lerp(GlassInnerLight, GlassBorderHigh, 0.20f)
                } else {
                    palette.textPrimary.copy(alpha = 0.05f)
                },
                faceShade = if (glass) GlassBottomShade else palette.shadowInk.copy(alpha = 0.16f),
                flapShade = palette.shadowInk,
                flapSpecular = if (glass) GlassBorderHigh else palette.textPrimary.copy(alpha = 0.18f),
            )
        }
    }
}

/**
 * 单张翻牌卡片：显示 [value]（按 [digits] 补零）。
 *
 * [animate] = false 时直接换数字（关动画 / 系统动画时长为 0 的场景）。
 */
@Composable
fun FlipCard(
    value: Int,
    modifier: Modifier = Modifier,
    digits: Int = 2,
    animate: Boolean = true,
    cornerRadius: Dp = 10.dp,
    durationMs: Int = FlipClockDefaults.DurationMs,
    colors: FlipClockColors = FlipClockDefaults.colors(),
) {
    val text = value.coerceAtLeast(0).toString().padStart(digits, '0').takeLast(digits)
    // 「翻走的那一页」：动画结束才追上当前值
    var previous by remember { mutableIntStateOf(value) }
    // 每个新值都拿一个从 0 开始的新 Animatable：新值进入组合的第一帧就是 p=0，
    // 不会先画一帧上一轮残留的 p=1（那会闪一下新值再退回旧值）。
    val progress = remember(value) { Animatable(0f) }

    LaunchedEffect(value, animate) {
        if (value == previous) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!animate) {
            previous = value
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // 新的 Animatable 已经是 0，不用再 snapTo；直接起手
        progress.animateTo(1f, tween(durationMs, easing = Motion.EaseFlip))
        previous = value
    }

    val prevText = previous.toString().padStart(digits, '0').takeLast(digits)
    // 🔴 flipping 判据用 previous != value 而不是 p < 1f：页片撤下和 previous 追平
    //    必须发生在同一次重组，否则会闪一帧"页片已撤、静态下半还是旧值"的混合数字
    val flipping = previous != value
    val localDensity = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    // 时钟是固定尺寸的显示面：数字不跟系统字号放大，否则 150% 时会撑出卡片。
    // 测量密度把 fontScale 钉在 1，字号按卡片高度算；读屏时间另由 semanticsLabel 提供。
    val fixedDensity = remember(localDensity) {
        Density(density = localDensity.density, fontScale = 1f)
    }
    val textMeasurer = remember(fontResolver, fixedDensity, layoutDirection) {
        TextMeasurer(fontResolver, fixedDensity, layoutDirection)
    }

    BoxWithConstraints(
        modifier = modifier.graphicsLayer {
            // 等价于 split-flap 的 overflow:hidden：页片绕铰链 3D 旋转时投影会超出卡片，
            // 只有 layer 级的 clip 能裁住整个子树（含 3D 变换）。
            clip = true
            shape = RoundedCornerShape(cornerRadius)
        },
    ) {
        // 字号跟**整卡高度**走（不是半页高度）；0.90 让数字视觉高度约占卡高 7 成，
        // 跨过中线后上下各露一半 —— 参考图的比例。
        val fontStyle = remember(colors.ink, maxHeight, localDensity) {
            TextStyle(
                color = colors.ink,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = (maxHeight.value * 0.90f).sp,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        }
        val layoutNew = remember(text, fontStyle) { textMeasurer.measure(text, fontStyle) }
        val layoutOld = remember(prevText, fontStyle) { textMeasurer.measure(prevText, fontStyle) }
        // 字体度量的视觉中心补偿：Roboto 上/下半富量差 27/1000 em，半个差即 0.0135 em
        val centerFixPx = with(fixedDensity) { (fontStyle.fontSize.value * 0.0135f).dp.toPx() }
        val radiusPx = with(localDensity) { CornerRadius(cornerRadius.toPx()) }
        val half = maxHeight / 2

        // 静态底层：上半 = 新值（被页片正面盖住，翻走后露出）；
        // 下半整段都用旧值 —— 新下半由页片背面带着落下，p=1 盖满后才撤页片换新。
        // 之前 p≥0.5 就切新：后半程静止层已是新数字，落下的页片与它内容相同，
        // 慢放时会读成"新数字上浮着一层自身镜像"，不像纸片盖住旧数字。
        Canvas(Modifier.fillMaxSize()) {
            val fullHeight = size.height
            val split = fullHeight / 2f
            val p = progress.value
            val bottomLayout = if (flipping) layoutOld else layoutNew
            clipRect(0f, 0f, size.width, split) {
                drawCardFace(layoutNew, colors, fullHeight, radiusPx, centerFixPx)
            }
            clipRect(0f, split, size.width, fullHeight) {
                drawCardFace(bottomLayout, colors, fullHeight, radiusPx, centerFixPx)
            }
            // 页片朝观众倒下时，下半片铰链处被遮光（后半程页片自己会盖住，不必画）
            if (flipping) {
                if (p < 0.5f) {
                    val cast = 0.22f * sin(PI * (p / 0.5f)).toFloat()
                    clipRect(0f, split, size.width, fullHeight) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.flapShade.copy(alpha = cast),
                                    colors.flapShade.copy(alpha = 0f),
                                ),
                                startY = split,
                                endY = fullHeight,
                            ),
                        )
                    }
                }
            }
        }

        if (flipping) {
            // 一页真翻牌：页片 = 上半页，绕分割线 0°→180° 一翻到底。
            // p 只在图层/绘制阶段读，翻页期间不重组。
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(half)
                    .graphicsLayer {
                        rotationX = -180f * progress.value
                        // 更大的相机距离 = 更克制的透视；12 有点"玩具"，16 更接近实体页片
                        cameraDistance = 16f * density
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val p = progress.value
                    val front = p < 0.5f
                    // 页片自身只有半卡高，但绘制坐标仍按整卡（2×本画布高度）
                    val fullHeight = size.height * 2f
                    val phase = if (front) p / 0.5f else (p - 0.5f) / 0.5f
                    // 明暗跟**页片角度**走：sin(πp) 在 0°/180° 为 0、90° 最大，
                    // 前后半程在铰链处连续，不再有线性斜率的拐点。
                    // 阴影画在数字下方（见 drawCardFace），纸面变暗但数字始终可读。
                    val dark = 0.46f * sin(PI * p).toFloat()
                    if (front) {
                        drawCardFace(
                            layoutOld, colors, fullHeight, radiusPx, centerFixPx,
                            shadeTop = dark,
                            shadeBottom = dark * 0.30f,
                        )
                    } else {
                        // 背面 = 新下半 + 垂直镜像；180° 时外层旋转把它正回来
                        drawCardFace(
                            layoutNew, colors, fullHeight, radiusPx, centerFixPx,
                            mirrorY = true,
                            shadeTop = dark * 0.45f,
                            shadeBottom = dark,
                        )
                    }
                    if (!front) {
                        // 自由边落地前的反光：软光带，不是 2dp 硬白线 ——
                        // 硬线在 3x 屏上会被读成描边，软光带才像受光。
                        val band = 12.dp.toPx()
                        val spec = 0.30f * sin(PI * phase).toFloat()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to colors.flapSpecular.copy(alpha = spec),
                                0.45f to colors.flapSpecular.copy(alpha = spec * 0.25f),
                                1f to colors.flapSpecular.copy(alpha = 0f),
                                startY = 0f,
                                endY = band,
                            ),
                            size = Size(size.width, band),
                        )
                    }
                }
            }
        }

        // 铰链：上暗下亮的凹槽，固定不动，压在翻页之上
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.verticalGradient(
                        0f to colors.flapShade.copy(alpha = 0.55f),
                        0.5f to colors.flapShade.copy(alpha = 0.55f),
                        0.5f to colors.hinge,
                        1f to colors.hinge,
                    ),
                ),
        )
    }
}

/**
 * 在 [DrawScope] 里画**一整个卡面**（底、玻璃层、描边、数字）。
 *
 * 调用方负责裁剪出自己需要的半页；画布高度可以是整卡高（静态层），
 * 也可以是半卡高（页片层，此时传 `fullHeight = 2 × 本画布高`）。
 * [mirrorY] 把整卡绕中轴垂直镜像 —— 页片背面的内容预镜像，
 * 翻到 180° 时被外层旋转正回来。
 */
private fun DrawScope.drawCardFace(
    layout: TextLayoutResult,
    colors: FlipClockColors,
    fullHeight: Float,
    radius: CornerRadius,
    centerFixPx: Float,
    mirrorY: Boolean = false,
    shadeTop: Float = 0f,
    shadeBottom: Float = 0f,
) {
    val w = size.width
    withTransform({
        if (mirrorY) {
            translate(0f, fullHeight)
            scale(1f, -1f, Offset.Zero)
        }
    }) {
        // 卡面必须**不透明**：翻页片是实体纸片，静止层不能从它背面透出来。
        // 玻璃感由顶光/底暗缘/描边承担，而不是靠卡面透明度。
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(colors.faceTop.copy(alpha = 1f), colors.faceBottom.copy(alpha = 1f)),
                startY = 0f,
                endY = fullHeight,
            ),
            size = Size(w, fullHeight),
            cornerRadius = radius,
        )
        // 玻璃：顶缘受光 → 中段透明 → 底缘背光。用同 RGB 的 0 alpha 收尾，
        // 避免和 Color.Transparent 插值时中段发灰。
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to colors.faceLight,
                0.16f to colors.faceLight.copy(alpha = 0f),
                0.74f to colors.faceShade.copy(alpha = 0f),
                1f to colors.faceShade,
                startY = 0f,
                endY = fullHeight,
            ),
            size = Size(w, fullHeight),
            cornerRadius = radius,
        )
        val stroke = 1.dp.toPx()
        drawRoundRect(
            color = colors.border,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(w - stroke, fullHeight - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
        // 翻页阴影压在卡面/描边之上、数字之下：纸面变暗，但数字保持可读，
        // 否则后半程的新数字会被一起压暗，看起来像一块半透明黑板。
        if (shadeTop > 0f || shadeBottom > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        colors.flapShade.copy(alpha = shadeTop),
                        colors.flapShade.copy(alpha = shadeBottom),
                    ),
                    startY = 0f,
                    endY = fullHeight,
                ),
                size = Size(w, fullHeight),
            )
        }
        drawText(
            textLayoutResult = layout,
            color = colors.ink,
            topLeft = Offset(
                x = (w - layout.size.width) / 2f,
                // 文字布局框中心对齐铰链（整卡中线），数字自然跨过分割线
                y = fullHeight / 2f - layout.size.height / 2f + centerFixPx,
            ),
        )
    }
}

/** 冒号：两个小圆点，颜色压一档，别抢数字 */
@Composable
private fun FlipColon(width: Dp, cardHeight: Dp, color: Color) {
    Column(
        modifier = Modifier.width(width).height(cardHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        repeat(2) { index ->
            if (index == 1) Box(Modifier.height(cardHeight * 0.14f))
            Box(
                Modifier
                    .width(cardHeight * 0.08f)
                    .height(cardHeight * 0.08f)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.7f)),
            )
        }
    }
}

/**
 * 翻牌时钟：[totalSeconds] → 分/秒（≥1 小时自动多一栏小时）。
 *
 * 一位一卡（老式翻牌钟的标准排法）：每张卡只放一个数字，数字才能撑满卡高、
 * 被分割线切成上下两截。只负责画，秒数的推进由宿主提供。
 *
 * 尺寸用固定卡高（[cardHeight]）而不是 `weight + aspectRatio`：
 * Row 里 weight 给宽、aspectRatio 推高的组合在约束不足时会算出无限高度。
 */
@Composable
fun FlipClock(
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    cardHeight: Dp = 88.dp,
    /** 宽/高，0.82 ≈ 接近正方形 */
    cardAspect: Float = 0.82f,
    gap: Dp = 6.dp,
    colonWidth: Dp = 12.dp,
    cornerRadius: Dp = 10.dp,
    durationMs: Int = FlipClockDefaults.DurationMs,
    colors: FlipClockColors = FlipClockDefaults.colors(),
    /** 左右留给外层环/容器的呼吸位，算卡宽时先扣掉 */
    horizontalInset: Dp = 0.dp,
    /**
     * 外圈是一个**圆**时的内缘位置：从容器边缘到圆内缘的距离。
     *
     * 给了就在上面那条"扣左右边距"之外，再加一道内切约束：
     * 整行（含四角）必须落进这个圆，且与圆内缘至少留 [fitClearance]。
     * 🔴 为什么必须有：圆不是矩形 —— 卡片上/下缘处的弦比赤道窄，
     *    只扣一个固定左右边距（原先是 12dp）时，4 位卡行的行宽会**超过环的直径**
     *    （360dp 屏：行宽/环径 = 1.06），四角必然切进环描边。
     *    见 RingTimer 的夜航分支。
     */
    fitInset: Dp? = null,
    /** 卡片四角与圆内缘之间必须留出的空气（只选了 [fitInset] 时生效） */
    fitClearance: Dp = 10.dp,
    /**
     * **始终**带小时栏（2026-10-25 他定：所有主题统一成 `时:分:秒`）。
     *
     * 打开后 <1 小时也显示一张 `0` 小时卡，卡数恒为 5 —— 好处是跨过 1 小时
     * 不再重排（不会被"多出一张卡 → 卡片变窄"吓一跳），而且和其它主题的
     * `0:45:00` 写法对得上。代价是短计时少一格宽度（84 → 约 72dp）。
     */
    alwaysShowHours: Boolean = false,
    /** TalkBack / 读屏用的时间文本；Canvas 画的数字本身没有语义 */
    semanticsLabel: String? = null,
) {
    val safe = totalSeconds.coerceAtLeast(0)
    // alwaysShowHours：恒占一格小时（0 也画），卡数不随跨小时变化
    val hasHours = alwaysShowHours || safe >= 3600
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    // 小时恒占两位（"01:34:00"）：和文本主题的 formatClockHms 对得上，
    // 也不会出现"单张 0 挂在最前面"那种难看的排法
    val hourDigits = if (hasHours) 2 else 0
    val slots = hourDigits + 4
    val colonCount = if (hasHours) 2 else 1
    val gapCount = slots + colonCount - 1
    val spokenTime = remember(hasHours, hours, minutes, seconds) {
        if (hasHours) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
    val label = semanticsLabel ?: spokenTime

    BoxWithConstraints(
        modifier = modifier.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // 按可用宽度反推卡高：≥1 小时会多一栏，固定 84dp 会顶出圆环/屏幕。
        // 4 卡时算出的卡高大于配置值，保持原尺寸；多卡时自动缩小。
        // 多一栏小时时再让一点，避免缩卡后仍压到圆环笔画
        val contentInset = horizontalInset + if (hasHours) 8.dp else 0.dp
        val contentWidth = (maxWidth - contentInset * 2).coerceAtLeast(0.dp)
        val totalFixed = colonWidth * colonCount.toFloat() + gap * gapCount.toFloat()
        // ① 宽度上限：整行不能超出可用宽（没有外圈时就是唯一约束）
        val byWidth = if (contentWidth.value.isFinite()) {
            (contentWidth - totalFixed) / slots.toFloat() / cardAspect
        } else {
            cardHeight
        }
        // ② 圆内切上限：整行四角必须落进圆（见 FlipClockFit）。
        //    几何是纯函数，算法与断言都在 FlipClockFitTest 里。
        val byCircle = if (fitInset == null) {
            cardHeight
        } else {
            FlipClockFit.maxCardHeightInCircle(
                circleHalfWidth = maxWidth.value / 2f - fitInset.value - fitClearance.value,
                slots = slots,
                colonCount = colonCount,
                cardAspect = cardAspect,
                colonWidth = colonWidth.value,
                gap = gap.value,
            ).dp
        }
        val fittedHeight = minOf(byWidth, byCircle, cardHeight).coerceAtLeast(32.dp)
        val cardWidth = fittedHeight * cardAspect

        @Composable
        fun Digit(value: Int) {
            FlipCard(
                value = value,
                digits = 1,
                modifier = Modifier.width(cardWidth).height(fittedHeight),
                animate = animate,
                cornerRadius = cornerRadius,
                durationMs = durationMs,
                colors = colors,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (hasHours) {
                if (hourDigits == 2) Digit(hours / 10)
                Digit(hours % 10)
                FlipColon(width = colonWidth, cardHeight = fittedHeight, color = colors.ink)
            }
            Digit(minutes / 10)
            Digit(minutes % 10)
            FlipColon(width = colonWidth, cardHeight = fittedHeight, color = colors.ink)
            Digit(seconds / 10)
            Digit(seconds % 10)
        }
    }
}
