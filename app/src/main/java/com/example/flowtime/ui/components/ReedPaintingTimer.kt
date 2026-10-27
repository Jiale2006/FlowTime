package com.example.flowtime.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flowtime.ui.theme.TextPrimary
import com.example.flowtime.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin

// ── 纸底与钤印色 ─────────────────────────────────────────────
private val PaperTop = Color(247, 240, 216)
private val PaperBase = Color(250, 247, 238)
private val PaperBottom = Color(246, 243, 232)
private val SealRed = Color(178, 58, 58)
private val SealText = Color(250, 248, 245)

private fun clamp01f(x: Float) = x.coerceIn(0f, 1f)
private fun easeIO(t: Float) = if (t < 0.5f) 2f * t * t else 1f - Math.pow(-2.0 * t + 2.0, 2.0).toFloat() / 2f

/**
 * 水彩主题计时器：「芦苇河」原作**全程完整显示**，过半后芦苇逐支随风摆动。
 *
 * 🔴 2026-10-25 他要求"那层遮挡物不要设置透明度了" —— 原来画作会按进度被一层白雾
 *    （显影遮罩，DstIn 软边光斑）盖住：倒计时/番茄前半程整幅画都在"洗"，只有专注过半
 *    才接近全显。现在遮罩整段删除，画作从第一秒起就是完整的；随时间变化的只剩风与钤印。
 * - 风场（windActive 由 TimerScreen 决定，真实进度过半后启动）：
 *   底画（reed_base.png，芦苇区已补底）保持静止，
 *   每支芦苇（reed_s0..7.png，用户标注切割出的穗+茎+叶）绕**自己的根部**微旋转，
 *   摆相按行波从左向右扫过（根部钉死、穗尖划弧），远景支减半——动的全是原画真笔触；
 * - completed：走满 → 朱红钤印「心流」淡入；暂停 = 风停（颜料静止）；
 * - ghost：待机幻影（整画低透明度预告，不显影、不钤印、不出数字）。
 */
@Composable
fun ReedPaintingTimer(
    /** 显影进度 0..1：0 = 整幅画被湿气完全挡住，1 = 全部显完 */
    progress: Float,
    timeText: String,
    modeLabel: String,
    modifier: Modifier = Modifier,
    completed: Boolean = false,
    /** 风（芦苇摆动）是否激活：显影过半且专注进行中（待机/休息/暂停为 false） */
    windActive: Boolean = false,
    dimmed: Boolean = false,
    reducedMotion: Boolean = false,
    ghost: Boolean = false,
) {
    val context = LocalContext.current
    // 素材加载：**全部在 IO 线程**，并且应用启动时已预热（见 PaintingAssetsLoader）。
    // 原来这里三块 remember 里直接 decodeStream，切主题那一刻主线程被占 150ms。
    val assets by produceState(PaintingAssetsLoader.peek(), context) {
        value = PaintingAssetsLoader.load(context)
    }
    val stalkSpecs = assets?.specs ?: emptyList()
    val base = assets?.base
    val stalks = assets?.stalks ?: emptyList()
    val fallback = assets?.fallback
    val textMeasurer = rememberTextMeasurer()

    // 进场淡入：画作一出现就满屏动会显得"素材在抖"，2.6s 慢慢起来
    val ambient = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ambient.animateTo(1f, tween(2600, easing = FastOutSlowInEasing)) }

    val frameTime = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime.value = it / 1_000_000L }
        }
    }

    // 钤印淡入：只认 completed（待机把进度传 1 做幻影，不能误触发钤印）
    val sealAlpha = remember { Animatable(0f) }
    LaunchedEffect(completed) {
        if (completed) {
            sealAlpha.snapTo(0f)
            // 2026-10-23：600 → 900ms。盖章是"压下去再回弹"，太快像弹窗淡入
            sealAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        } else {
            sealAlpha.snapTo(0f)
        }
    }

    // 风：windActive 时淡入（1.8s），待机/休息/暂停归零
    val windAlpha = remember { Animatable(0f) }
    LaunchedEffect(windActive) {
        if (windActive) {
            windAlpha.snapTo(0f)
            windAlpha.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        } else {
            windAlpha.snapTo(0f)
        }
    }

    Box(modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
            contentDescription = "水彩芦苇河 · 专注进度画作",
        ) {
            val ts = frameTime.value
            val wind = (if (reducedMotion) 0f else ReedSceneWind.windAt(ts)) * windAlpha.value
            val tSecAll = ts * 0.001f

            // 纸底
            drawRect(
                brush = Brush.verticalGradient(listOf(PaperTop, PaperBase, PaperBottom)),
                size = size,
            )

            val img = base ?: fallback
            // 🔴 2026-10-25 他要求"计时前完全被遮挡，透不出一点画"：
            //    待机原来画 8% 的幻影预告，现在干脆整块不画 —— 只有纸底。
            if (img != null && !ghost) {
                // 方案 B：竖幅画改为铺满宽度（cover），优先保留底部芦苇/河岸，
                // 多余高度全部从顶部裁掉。显影光斑、风摆精灵都按同一个 rect 计算，
                // 裁切后仍与画面对齐。
                val scale = max(size.width / img.width, size.height / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                val dx = (size.width - dw) / 2f
                val dy = size.height - dh
                val rect = Rect(Offset(dx, dy), Size(dw, dh))
                val useStalks = base != null && stalks.none { it == null }

                drawIntoCanvas { canvas ->
                    // L1：底画 + 芦苇逐支 + 显影遮罩
                    val l1 = androidx.compose.ui.graphics.Paint().apply {
                        blendMode = BlendMode.SrcOver
                        alpha = 1f
                    }
                    canvas.saveLayer(rect, l1)

                    drawImage(
                        image = img,
                        dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                    )

                    if (useStalks) {
                        // 每支芦苇绕根部微旋转：行波按根部 x 从左向右扫。
                        // 2026-10-23 细化：幅度降一半（3.0→2.2）、细颤频率抬高（2.3→3.1）、
                        // 相位不再掺支序 i（原来 i*0.8 会让相邻支莫名错拍），改为按 rootX 派生频率
                        // （±18%）——每支略有自己的节奏，整片才不会像一块板在晃。
                        val tsec = ts * 0.001f
                        stalkSpecs.forEachIndexed { i, s ->
                            val sprite = stalks[i] ?: return@forEachIndexed
                            val dstX = dx + s.x * dw
                            val dstY = dy + s.y * dh
                            val dstW = s.sw * dw
                            val dstH = s.sh * dw
                            // 2026-10-25 重写：原来是两个正弦硬叠，一支和一支几乎同拍，
                            // 整片像一块板在晃。现在：
                            //  · 三个频段：主摆（0.86Hz 量级）+ 次摆（2.3Hz 量级）+ 细颤（5.4Hz 量级）
                            //    细颤只给近景，远景被空气阻尼吃掉；
                            //  · 相位按根部 x 派生 → 行波从左向右扫（风是有方向的）；
                            //  · 每支的"刚度"由自身高度决定：矮的硬、摆得少，高的软、摆得多。
                            val h = s.sh.coerceIn(0f, 1f)
                            val amp = (if (s.distant) 0.42f else 1f) * (0.74f + 0.42f * h)
                            val freq = 0.78f + (s.rootX * 5.3f % 0.44f)
                            val phase = s.rootX * 6.5f
                            val bend =
                                sin(tsec * 0.86f * freq - phase) +
                                    0.38f * sin(tsec * 2.30f * (0.72f + freq * 0.28f) - phase * 1.7f + 0.9f) +
                                    0.13f * sin(tsec * 5.40f * (0.90f + freq * 0.20f) - phase * 2.9f + 0.4f) *
                                    (if (s.distant) 0.35f else 1f)
                            val stiffness = 0.64f + 0.52f * (1f - h)
                            val ang = wind * amp * bend * 2.2f / stiffness
                            val pivot = Offset(dstX + s.px * dstW, dstY + s.py * dstH)
                            rotate(ang, pivot = pivot) {
                                drawImage(
                                    image = sprite,
                                    dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
                                    dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                                )
                            }
                        }
                    }

                    // 🔴 这里原来还有一层「L2 显影遮罩」（DstIn 软边光斑按作画顺序洇开）。
                    //    2026-10-25 他要求去掉：那层白雾在倒计时/番茄前半程一直盖着画，
                    //    观感像在"洗"一张画。现在只保留 L1 一次 restore。
                    // ── 活的层面（2026-10-25）─────────────────────────────
                    // 雾气（全域）/ 河面高光与涟漪（水域遮罩）/ 岸草风扫（植被遮罩）。
                    // 它们都画在 L1 图层内、紧接着被显影遮罩一起"洗"掉，
                    // 所以待机和刚开场时不会有一块地方自己动。
                    if (!ghost && ambient.value > 0.01f) {
                        drawMistDrift(rect, tSecAll, ambient.value)
                        assets?.waterMask?.let { mask ->
                            drawRiverLife(mask, rect, tSecAll, wind, ambient.value)
                        }
                    }

                    // L2：显影遮罩（软边光斑按作画顺序洇开）
                    // 2026-10-23 细化：原来每个斑只有「白 → 透明」两段色标，边缘是均匀正圆，
                    // 显影像一团团白雾。现在改成主斑多段柔边（0.78 起转软）+ 两个按自身方位
                    // 偏出的伴瓣，轮廓不再正圆；伴瓣只在洇开过半后出现，省掉前期的绘制开销。
                    val l2 = androidx.compose.ui.graphics.Paint().apply { blendMode = BlendMode.DstIn }
                    canvas.saveLayer(rect, l2)
                    sceneBlobs.forEach { b ->
                        val local = clamp01f((progress - b.w0) / (b.w1 - b.w0))
                        if (local <= 0f) return@forEach
                        val grow = 0.30f + 0.70f * easeIO(local)
                        val a = clamp01f(local * 6f)
                        val cc = Offset(rect.left + b.x * dw, rect.top + b.y * dh)
                        val r = b.r * dw * grow
                        drawRevealBlob(cc, r, a, showLobes = local > 0.22f, seed = b.x * 6.28f + b.y * 3.14f)
                    }
                    canvas.restore()
                    canvas.restore()
                }

                // 铺满宽度后画顶更接近云层/芦苇，给时间数字留一条纸色渐隐；
                // 待机幻影没有数字，不加，免得把预览洗淡。
                // 2026-10-23：120dp 两段 → 152dp 三段，过渡更长更柔，数字不再"悬空"
                if (!ghost) {
                    val fadeHeight = 152.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to PaperBase.copy(alpha = 0.74f),
                                0.46f to PaperBase.copy(alpha = 0.40f),
                                1f to Color.Transparent,
                            ),
                            startY = 0f,
                            endY = fadeHeight,
                        ),
                        size = Size(size.width, fadeHeight),
                    )
                }

                // 完成：钤印（朱红方章 + 白文，微旋淡入）。
                // 用可见画布而不是素材 rect 定位：顶部裁切后 rect.top 是负数，
                // 沿用原 rect 会把印章画到屏幕外。
                if (completed && sealAlpha.value > 0f) {
                    drawSeal(
                        Rect(Offset.Zero, size),
                        sealAlpha.value,
                        sc = 1.15f - 0.15f * easeIO(sealAlpha.value),
                        textMeasurer,
                    )
                }
            }
        }

        // 纸纹叠层：纤维 + 颗粒 + 四边做旧。**静态 Canvas**，不读 frameTime，
        // 所以不进帧循环（纹理每帧重画会白烧 400 次 draw）。
        PaperGrainOverlay(modifier = Modifier.fillMaxSize())

        // 数字与模式小字：压在画作上方干燥区（待机幻影不显示）
        if (!ghost) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = timeText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = modeLabel,
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

// ── 钤印 ─────────────────────────────────────────────────────
/**
 * 朱红方章。2026-10-23 加了"印泥"细节：
 * ① 章体外渗一圈极淡红（纸吸印泥会晕出 1–2px）；
 * ② 章体两次叠加、偏移 0.6dp → 印泥厚薄不匀，边缘不再是干净矢量矩形；
 * ③ 文字也错开一点点（同一次用力，但纸上会有微移）。
 */
private fun DrawScope.drawSeal(
    rect: Rect,
    alpha: Float,
    sc: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val cx = rect.left + rect.width * 0.82f
    val cy = rect.top + rect.height * 0.10f
    val half = 20.dp.toPx() * sc
    val bleed = 1.06f
    val nudge = 0.6.dp.toPx()
    rotate(-6f, pivot = Offset(cx, cy)) {
        // ① 外渗
        drawRoundRect(
            color = SealRed.copy(alpha = 0.10f * alpha),
            topLeft = Offset(cx - half * bleed, cy - half * bleed),
            size = Size(half * 2 * bleed, half * 2 * bleed),
            cornerRadius = CornerRadius(9.dp.toPx() * sc),
        )
        // ② 章体（错位两层）
        drawRoundRect(
            color = SealRed.copy(alpha = 0.86f * alpha),
            topLeft = Offset(cx - half + nudge, cy - half + nudge),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(7.dp.toPx() * sc),
        )
        drawRoundRect(
            color = SealRed.copy(alpha = 0.28f * alpha),
            topLeft = Offset(cx - half, cy - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(7.dp.toPx() * sc),
        )
        // ③ 白文
        val style = TextStyle(color = SealText.copy(alpha = 0.94f * alpha), fontSize = 15.sp)
        val m1 = textMeasurer.measure("心", style)
        val m2 = textMeasurer.measure("流", style)
        drawText(m1, topLeft = Offset(cx - m1.size.width / 2f + nudge * 0.5f, cy - half * 0.92f))
        drawText(m2, topLeft = Offset(cx - m2.size.width / 2f + nudge * 0.5f, cy + half * 0.92f - m2.size.height))
    }
}

// ── 纸纹（静态叠层） ─────────────────────────────────────────
private data class PaperFiber(
    val x: Float,
    val y: Float,
    val len: Float,
    val dy: Float,
    val alpha: Float,
    val dark: Boolean,
)

private data class PaperSpeck(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val alpha: Float,
    val dark: Boolean,
)

/**
 * 纸纤维 + 颗粒 + 四边做旧。
 *
 * 🔴 必须挂成**独立静态 Canvas**：它不读 frameTime，不进帧循环。
 * 若塞进画作那层 Canvas，150 根纤维 + 240 个颗粒会随每一帧重画。
 *
 * 颜色只取"暖灰/白"两种（亮暗各半）：全暗会显脏，全亮会显得像屏幕有油污。
 * 颗粒用固定种子，重组不会跳位置。
 */
@Composable
private fun PaperGrainOverlay(modifier: Modifier = Modifier) {
    val fibers = remember {
        val rnd = kotlin.random.Random(20261024)
        List(150) {
            PaperFiber(
                x = rnd.nextFloat() * 1.02f - 0.02f,
                y = rnd.nextFloat(),
                len = 0.020f + rnd.nextFloat() * 0.085f,
                dy = (rnd.nextFloat() - 0.5f) * 0.004f,
                alpha = 0.018f + rnd.nextFloat() * 0.026f,
                dark = rnd.nextInt(100) < 62,
            )
        }
    }
    val specks = remember {
        val rnd = kotlin.random.Random(20261025)
        List(240) {
            PaperSpeck(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                radiusDp = 0.40f + rnd.nextFloat() * 0.95f,
                alpha = 0.010f + rnd.nextFloat() * 0.024f,
                dark = rnd.nextBoolean(),
            )
        }
    }
    Canvas(modifier) {
        fibers.forEach { fiber ->
            drawLine(
                color = if (fiber.dark) {
                    Color(0xFF8C7659).copy(alpha = fiber.alpha)
                } else {
                    Color.White.copy(alpha = fiber.alpha * 0.85f)
                },
                start = Offset(size.width * fiber.x, size.height * fiber.y),
                end = Offset(size.width * (fiber.x + fiber.len), size.height * (fiber.y + fiber.dy)),
                strokeWidth = 0.7.dp.toPx(),
            )
        }
        specks.forEach { speck ->
            drawCircle(
                color = if (speck.dark) {
                    Color(0xFF7A6349).copy(alpha = speck.alpha)
                } else {
                    Color.White.copy(alpha = speck.alpha)
                },
                radius = speck.radiusDp.dp.toPx(),
                center = Offset(size.width * speck.x, size.height * speck.y),
            )
        }
        // 四边做旧：中心完全透明，只在外缘压一层极淡茶色
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Color(0xFF6B5334).copy(alpha = 0.10f),
                ),
                center = center,
                radius = size.maxDimension * 0.78f,
            ),
        )
    }
}

// ── 风场与显影光斑数据 ───────────────────────────────────────

private object ReedSceneWind {
    /**
     * 瞬时风强：三层不同周期的缓动正弦（约 14s / 5.7s / 2.4s，模拟风的长节奏）
     * 叠加一次约 20s 周期的宽缓阵风（sin^6，起落柔和、不尖刺）。
     * 值域约 [-1, +2]，乘到每支芦苇的摆角（度）系数 3.0f/0.7f 上。
     */
    fun windAt(t: Long): Float {
        val tt = t.toFloat()
        val base = 0.50f * sin(tt * 0.00045f) +
            0.28f * sin(tt * 0.0011f + 1.3f) +
            0.16f * sin(tt * 0.0026f + 0.5f)
        val gust = Math.pow(Math.max(0.0, sin(tt * 0.00032f + 0.8f).toDouble()), 6.0).toFloat() * 1.1f
        return base + gust
    }
}

private fun DrawScope.drawRevealBlob(
    center: Offset,
    radius: Float,
    alpha: Float,
    showLobes: Boolean,
    seed: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = alpha),
                0.58f to Color.White.copy(alpha = alpha * 0.94f),
                0.78f to Color.White.copy(alpha = alpha * 0.62f),
                0.92f to Color.White.copy(alpha = alpha * 0.22f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    if (!showLobes) return
    // 两个伴瓣，角度由 seed（该斑在画面上的位置）决定 → 各斑形状不同
    val lobeSpecs = floatArrayOf(0.44f, 0.36f)
    lobeSpecs.forEachIndexed { k, sizeF ->
        val ang = seed + k * 2.4f
        val distance = radius * 0.40f
        val c = Offset(center.x + cos(ang) * distance, center.y + sin(ang) * distance)
        val rr = radius * sizeF
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = alpha * 0.46f),
                    0.66f to Color.White.copy(alpha = alpha * 0.26f),
                    1f to Color.Transparent,
                ),
                center = c,
                radius = rr,
            ),
            radius = rr,
            center = c,
        )
    }
}

private val sceneBlobs = listOf(
    PaintBlob(0.50f, 0.06f, 0.34f, 0.00f, 0.16f),
    PaintBlob(0.15f, 0.15f, 0.26f, 0.05f, 0.22f),
    PaintBlob(0.86f, 0.13f, 0.28f, 0.10f, 0.28f),
    PaintBlob(0.08f, 0.36f, 0.30f, 0.16f, 0.36f),
    PaintBlob(0.93f, 0.34f, 0.26f, 0.22f, 0.42f),
    PaintBlob(0.50f, 0.34f, 0.24f, 0.28f, 0.48f),
    PaintBlob(0.84f, 0.52f, 0.26f, 0.34f, 0.54f),
    PaintBlob(0.46f, 0.55f, 0.24f, 0.42f, 0.62f),
    PaintBlob(0.24f, 0.76f, 0.30f, 0.52f, 0.72f),
    PaintBlob(0.74f, 0.78f, 0.28f, 0.60f, 0.80f),
    PaintBlob(0.50f, 0.94f, 0.36f, 0.72f, 0.90f),
    PaintBlob(0.04f, 0.94f, 0.30f, 0.80f, 0.95f),
    PaintBlob(0.96f, 0.94f, 0.30f, 0.84f, 1.00f),
)

private class PaintBlob(val x: Float, val y: Float, val r: Float, val w0: Float, val w1: Float)
