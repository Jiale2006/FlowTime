package com.example.flowtime.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.AccentGreen
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.ChampagneBright
import com.example.flowtime.ui.theme.ImperialGold
import com.example.flowtime.ui.theme.MonoInk
import com.example.flowtime.ui.theme.ParticleBlue
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.PrimaryLight
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.ThemeMode
import com.example.flowtime.ui.theme.WatercolorRose

/**
 * 单色极简的计时线宽：**计时就是单色的一根线**（用户 2026-10-16 定），
 * 与其余主题 14dp 的"管"无关，故单独定义，不跟随 strokeWidth 形参。
 */
private val MonoLineWidth = 2.dp

/**
 * 粒子系统的环线宽：**冷蓝细环**（用户 2026-10-16 选的 2B 方案 —— 精密仪器感，
 * 不是其余主题 14dp 的"管"），同样与 strokeWidth 形参无关，单独定义。
 */
private val ParticleLineWidth = 3.dp

/**
 * 玻璃拟态的环线宽（2026-10-24 表壳化）：环不再是"发光细带"，而是翻牌钟的表壳。
 * 实体卡片的描边只有 1px，环若维持 8dp 圆头就成了另一种材质语言；
 * 收到 6dp、平头收口，进度靠**宽度**压过 1.5dp 的发丝轨道，不靠亮度堆叠。
 * 6dp 而不是 4dp：翻牌的冒号圆点约 6.7dp，进度线若比标点还细，层级就反了。
 */
private val NocturneLineWidth = 6.dp

/** 表壳轨道：一圈发丝线，只交代"总时长"，与卡片的 1px 描边同一种线条。 */
private val NocturneTrackWidth = 1.5.dp

/**
 * 宫廷御制的环线宽：3dp 描金细弧。提示词「鎏金只做章」—— 金色是细线，
 * 不是 14dp 的金镏子；轨道也是同宽的金 10% 发丝线。
 */
private val ImperialLineWidth = 3.dp

/**
 * 环形倒计时（教程 §3.1）：底色弧 + 渐变进度弧，圆角线帽，弧首定位点，中央大号时间。
 *
 * 🔴 环区为**纯静态**绘制（用户要求"简洁"，2026-10-14 定）：整环光带 / 环境粒子 / 秒级涟漪 /
 *    进度内流光 / 弧首彗尾 / 背景散点 / 刻度线 已全部移除。
 *    保留的只是各主题的**静态材质差异**（如玻璃的管内液体光 + 镜面细线、水彩的补笔渗色）。
 *
 * 全部纯 Canvas 绘制（不用图层，防页面切换残影）。
 * 🔴 已无帧驱动循环（原来那个 `while(true) + withFrameNanos` 随着笔尖粒子一起删了）——
 *    环形区不再持续重绘，只在 progress 变化时重组。
 *
 * 主题字形差异：水彩 = 衬线斜体数字 + 水墨洇边；其余主题 = 直立衬线。
 * 单色极简的环 = **一根 2dp 墨黑线**：极浅灰底轨 + 墨黑进度线，无端点标记、恒色不偏色。
 *
 * ⚠️ **水彩根本不经过本组件**：`TimerScreen.kt` 里 `mode == WATERCOLOR` 走的是 `ReedPaintingTimer`。
 *    所以本文件内所有 `ThemeMode.WATERCOLOR` 分支都是**死代码**（`dimFadeMs` 条目 / Canvas 水彩补笔 /
 *    `tickFadeMs` 的 500 / 时钟字体 / 数字水墨洇影副本，共 5 处）。**保留 ≠ 有效**，
 *    不要照它们推导水彩的实际观感（2026-10-16 已因 `tickFadeMs = 500` 误判过一次）。
 */
@Composable
fun RingTimer(
    progress: Float,
    timeText: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    timeColor: Color = TextPrimary,
    /** 暂停态：整体降透明度，形成明确状态区分 */
    dimmed: Boolean = false,
    /**
     * 颜料色用的进度（0..1）。与 progress 分开：待机时几何上画满圈
     * （progress=1 作为"画布"），但颜料应显示起步色，而不是误判成"时间将尽"。
     */
    pigmentProgress: Float = progress,
    centerContent: (@Composable () -> Unit)? = null,
    /** 玻璃拟态：主按钮被按住时顶缘受光抬一档（Liquid Glass 的交互受光，只对 NOCTURNE 生效） */
    glassHighlighted: Boolean = false,
    /** 夜航（玻璃拟态）翻牌时钟用的秒数；别的主题忽略 */
    timeSeconds: Int = 0,
    /** 翻牌动画开关（系统"移除动画"或用户关动效时给 false） */
    flipAnimate: Boolean = true,
) {
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    // 单色极简专属：2dp 细线的像素宽（环中线位置仍沿用 inset，与外主题对齐）
    val monoLinePx = with(LocalDensity.current) { MonoLineWidth.toPx() }
    // 粒子系统专属：3dp 细环 + 外缘辉光的像素宽
    val particleLinePx = with(LocalDensity.current) { ParticleLineWidth.toPx() }
    // 玻璃拟态：Apple 式单笔环的线宽
    val nocturneLinePx = with(LocalDensity.current) { NocturneLineWidth.toPx() }
    // 表壳轨道：发丝线（只交代总时长）
    val nocturneTrackPx = with(LocalDensity.current) { NocturneTrackWidth.toPx() }
    // 宫廷御制：描金细弧的像素宽
    val imperialLinePx = with(LocalDensity.current) { ImperialLineWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)

    // ---- 主题动效参数：水彩=液态缓动（≥500ms ease-in-out，禁快速缓动）----
    // 其余主题的节奏等各自提示词到位后再单独调制。
    val mode = ActivePalette.mode
    // 🔴 环的边距必须按**本主题实际线宽**算：`strokeWidth` 形参是 14dp 的"管"，
    //    玻璃拟态的环只有 6dp。沿用 14dp 那套会平白缩掉 11dp 半径 —— 而 11dp
    //    在屏幕上正好就是"卡片压环"的那点余量（360dp 屏，半径 127.6 → 138.8）。
    val ringInsetPx = if (mode == ThemeMode.NOCTURNE) {
        nocturneLinePx / 2f + nocturneLinePx * 0.9f
    } else {
        strokePx / 2f + strokePx * 0.9f
    }
    val ringInsetDp = with(LocalDensity.current) { ringInsetPx.toDp() }
    val nocturneHalfLineDp = with(LocalDensity.current) { (nocturneLinePx / 2f).toDp() }
    val dimFadeMs = when (mode) {
        // 玻璃拟态禁用短促缓动（状态过渡要有"流体"感）。
        // 单色极简：Deliberate Slowness（风格规范 duration-500~700 的慢节奏）
        ThemeMode.NOCTURNE, ThemeMode.MONO -> 600
        // 粒子系统：Quantum Snap —— 提示词要求前景反馈精确快速（duration-150~200）。
        // 这条只管"控件级状态反馈"（暂停/恢复的整体降透明），跳秒数字另算（见 tickFadeMs）。
        ThemeMode.PARTICLES -> 200
        else -> 300
    }
    // 玻璃拟态的暂停/恢复走**弹簧**（Apple：状态切换用弹簧而非固定时长，天然可打断）：
    // damping 1.0 = 临界阻尼不回弹，stiffness 439 ≈ response 0.3s（(2π/0.3)²）。
    // 原来 600ms 的 tween 按下去要等 0.6 秒才暗，手感像"没点着"。
    val ringAlphaSpec: AnimationSpec<Float> = if (mode == ThemeMode.NOCTURNE) {
        spring(dampingRatio = 1f, stiffness = 439f)
    } else {
        tween(dimFadeMs, easing = FastOutSlowInEasing)
    }
    val ringAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.55f else 1f,
        animationSpec = ringAlphaSpec,
        label = "ringDim",
    )
    // 受光响应：0 = 常规 0.55，1 = 按住时 0.70（弹簧回落，不弹跳）
    val highlightBoost by animateFloatAsState(
        targetValue = if (glassHighlighted) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 632f),
        label = "glassHighlight",
    )

    // ---- 颜料色：随剩余时间流动（颜料进度 0→1 即剩余时间 1→0）----
    val pigmentBase = if (mode == ThemeMode.MONO) {
        // 单色极简：恒墨黑。一根线不随进度变色，收尾也不偏色 ——
        // 原来会把 else 分支的 WatercolorRose（粉红）引进单色主题，属色相污染。
        MonoInk
    } else if (mode == ThemeMode.PARTICLES) {
        // 粒子系统：恒冷蓝（用户选的是"单色冷蓝细环"）。收尾不引暖色 ——
        // 提示词禁暖色/大地色主调，红粉会直接破掉"深空"的冷。
        ParticleBlue
    } else if (mode == ThemeMode.IMPERIAL) {
        // 宫廷御制：恒鎏金（唯一正色）。环随进度变色会把第二面色引进深宫，
        // 紧迫感由进度长度自己表达，不靠"变红"。
        ImperialGold
    } else {
        val pigmentP = pigmentProgress.coerceIn(0f, 1f)
        val settled = lerp(AccentGreen, Primary, (pigmentP / 0.85f).coerceIn(0f, 1f))
        val urgent = ((pigmentP - 0.88f) / 0.12f).coerceIn(0f, 1f)
        // 玻璃拟态的紧迫色是"更亮的香槟"（唯一强调色）；水彩/涂鸦用玫瑰/红笔
        val urgentColor = when (mode) {
            ThemeMode.NOCTURNE -> ChampagneBright
            else -> WatercolorRose
        }
        lerp(settled, urgentColor, urgent * 0.75f)
    }
    val pigmentLight = lerp(pigmentBase, PrimaryLight, 0.45f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // 🔴 无障碍：环是纯 Canvas，本身**没有任何语义** —— 读屏用户原来只听到中间的数字，
            //    听不出"进行到哪了"。这里把整块标成一个进度条节点（mergeDescendants 让中央文案
            //    并进同一个节点：TalkBack 一次读「75%，剩余 12:34」）。
            //    值用 clamped（1 = 满环），倒计时语义即"还剩多少"。
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        // 环区为纯静态绘制（用户要求简洁：光带 / 粒子 / 涟漪 / 流光 / 笔尖特效全部移除）：
        // 只留 底色弧 + 进度弧 + 弧首定位点 + 中央时间。
        // 不再用 graphicsLayer 承载整体透明度：整块图层在页面切换时容易留下残影。
        // 改成把 ringAlpha 直接乘进每次绘制的颜色里 —— 视觉一致，但不需要图层。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = ringInsetPx
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // 环区为纯静态绘制：只有两笔（轨道 + 进度）+ 弧首圆点。
            // 五套主题各自的画法见 RingPainters.kt —— 本文件只管摆放，不管笔触。
            // 暂停暗化不用图层透明度，而是把 ringAlpha 乘进每个颜色（防页面切换残影）。
            val frame = RingFrame(
                topLeft = Offset(inset, inset),
                arcSize = arcSize,
                center = Offset(size.width / 2, size.height / 2),
                radius = arcSize.minDimension / 2,
                canvasSize = size,
                strokePx = strokePx,
                monoPx = monoLinePx,
                particlePx = particleLinePx,
                nocturnePx = nocturneLinePx,
                nocturneTrackPx = nocturneTrackPx,
                imperialPx = imperialLinePx,
                ringAlpha = ringAlpha,
                highlightBoost = highlightBoost,
                pigmentBase = pigmentBase,
                pigmentLight = pigmentLight,
            )
            drawRingTrack(mode, frame)
            drawRingProgress(mode, frame, clamped)
        }

        Column(
            // 暂停时与环同步变暗：环的 ringAlpha 已经是动画值，中心数字/文案跟着一起淡，
            // 否则会出现"环停了、数字还亮着"的状态歧义。
            modifier = Modifier.graphicsLayer { alpha = ringAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 主题节奏：水彩=500ms 液态晕开（用户验收过的洇墨感）；
            // 玻璃=250ms —— 跳秒是每秒高频更新，motion.md 要求频繁交互少动效，
            // 500ms 交叉淡化会让新旧数字长时间叠印重影，250ms 一闪即过
            val tickFadeMs = when (mode) {
                // ⚠️ 死值：玻璃拟态已改成**竖向滚动 + 弹簧**（见下面的 transitionSpec），
                //    这个 250 现在只当 else 分支的兜底值，别再拿它描述夜航的观感。
                ThemeMode.NOCTURNE -> 250
                // 单色极简：**硬切**（0 = 不做交叉淡化）。原来跟着规范抄了 500ms，
                // 但数字每秒才变一次 —— 500ms 淡入 + 500ms 淡出等于新旧数字有一半
                // 时间半透明叠印，且每次切换都打断上一次过渡，看着发虚"不干脆"。
                // 秒表数字瞬时替换最清晰。（夜航当初就是为同一个坑从 500 收到 250。）
                ThemeMode.MONO -> 0
                // 粒子系统也走下面的 120：提示词的 duration-150~200 是给**控件交互**的，
                // 跳秒数字每秒才变一次，套 200ms 就有 20% 时间新旧数字半透明叠印
                //（单色极简已因同一个坑从 500 收到硬切）。控件级节奏见 dimFadeMs。
                else -> 120
            }
            // 时钟容器宽度钉死在最宽组合上：AnimatedContent 交替时容器会在新旧
            // 字符串宽度之间动画收缩（衬线数字不等宽），一收缩旧数字右缘就被裁掉
            // ——表现为跳秒瞬间"最右边被遮挡"。钉死宽度后新旧两帧在同一容器里
            // 交叉淡化，顺带消除了每秒的水平抖动。
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val clockWidthDp = remember(mode, density) {
                val clockStyle = TextStyle(
                    // 单色极简与粒子系统走等宽（font-mono）
                    fontFamily = when (mode) {
                        ThemeMode.MONO -> FontFamily.Monospace
                        // 粒子系统：等宽（提示词 font-mono）—— 科技感，跳秒也不抖
                        ThemeMode.PARTICLES -> FontFamily.Monospace
                        else -> FontFamily.Serif
                    },
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                )
                val widestPx = maxOf(
                    // 时:分:秒 固定 8 字符（2026-10-25 起所有主题都带小时），
                    // 只量 "88:88" 会让 "0:45:00" 超出槽宽被折行/裁掉
                    textMeasurer.measure("88:88:88", clockStyle).size.width,
                    textMeasurer.measure("888:88", clockStyle).size.width,
                )
                with(density) { widestPx.toDp() } + 8.dp
            }
            // 数字跳动（跳秒脉冲缩放）已按用户要求移除：每秒放大回缩看着心悸。
            // 保留固定宽度容器与水墨晕影；跳秒时只有文字内容静态更新。

            Box(contentAlignment = Alignment.Center) {
                // 🔴 玻璃拟态必须**逐位**滚动：整串 AnimatedContent(targetState = "12:34")
                //    会让 5 个字符一起滑 —— 秒数一变"整块时间"都在动。秒表只该动变化的那一位。
                if (mode == ThemeMode.NOCTURNE) {
                    // 🔴 玻璃拟态改用机械翻牌（2026-10-24 他要求）：秒级跳动从"逐位滚动"
                    //    换成绕分割线的 3D 翻页，只有变化的那一位会翻。宽度沿用时钟槽宽，
                    //    卡片按 aspectRatio 自己分摊；数字色跟主题时间线（月光蓝→香槟）走。
                    FlipClock(
                        totalSeconds = timeSeconds,
                        animate = flipAnimate,
                        // 一位一卡（4 张）：卡高由**环的内切圆**反推，不再写死 84dp ——
                        // 84dp 是"环心能塞多大"，但卡片行四角其实顶进了环描边。
                        cardHeight = 84.dp,
                        cornerRadius = 8.dp,
                        // 环内缘 = 边距 + 半个描边；卡片四角与它留 10dp 空气。
                        // 固定扣 12dp 只够赤道用：4 位时行宽 / 环径 = 1.06（行比环还宽）。
                        fitInset = ringInsetDp + nocturneHalfLineDp,
                        fitClearance = 10.dp,
                        horizontalInset = 12.dp,
                        // 时:分:秒 恒 5 位（与其它主题的 formatClock 对齐）
                        // 不到一小时不占小时卡（他要的 "34:00" 而不是 "0:34:00"）
                alwaysShowHours = false,
                    )
                    // 文案挪到环外后就不占环心了（见下面的根 Box 层）
                } else {
                    AnimatedContent(
                        targetState = timeText,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            when {
                                // 硬切：不产生任何过渡帧 —— 数字瞬时替换（见 tickFadeMs 注释）
                                tickFadeMs == 0 ->
                                    EnterTransition.None togetherWith ExitTransition.None

                                else -> fadeIn(tween(tickFadeMs, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(tickFadeMs, easing = FastOutSlowInEasing))
                            }
                        },
                        label = "timeTick",
                    ) { text ->
                        ClockText(
                            text = text,
                            color = timeColor,
                            fontFamily = when (mode) {
                                // 单色极简与粒子系统走等宽（font-mono）
                                ThemeMode.MONO -> FontFamily.Monospace
                                ThemeMode.PARTICLES -> FontFamily.Monospace
                                else -> FontFamily.Serif
                            },
                            modifier = Modifier.width(clockWidthDp),
                        )
                    }
                }
            }
            // 夜航的文案挂在**根 Box** 上（见下），不跟着卡片列走 ——
            // 只有挂根 Box，BottomCenter 才是"表盘方块的底边"，才可能落到环外。
            if (mode != ThemeMode.NOCTURNE) centerContent?.invoke()
        }

        // 玻璃拟态的文案：环外、表盘正下方（2026-10-25 他定"环里只留表壳+翻牌"）。
        // 偏移 22dp = 文案高 + 5dp：文案顶边落在方块底边下方 ≈5dp，
        // 而环的外缘本来就比方块底边高 5.4dp（边距 8.4 − 半个描边 3），
        // 合起来正好是环下方约 10dp 的空气。
        // 与数字同样吃 ringAlpha，暂停时一起变暗。
        if (mode == ThemeMode.NOCTURNE && centerContent != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 22.dp)
                    .graphicsLayer { alpha = ringAlpha },
            ) {
                centerContent?.invoke()
            }
        }
    }
}

/**
 * 时钟数字：默认衬线 Light + 宽字距。水彩走斜体（风格规则「文字使用衬线斜体」），
 * 单色极简与粒子系统走等宽（font-mono），其余主题正体。
 *
 * ⚠️ 曾经的"渐变文字"通路（`BlendMode.SrcIn` 遮罩）随现代渐变主题一起删除 ——
 * 那条路必须配 `CompositingStrategy.Offscreen` 才不会连带改写身后像素，历史踩过坑。
 * 以后若再要做渐变字，别忘图层隔离。
 */
@Composable
private fun ClockText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Serif,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        fontSize = 64.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp,
        color = color,
    )
}
