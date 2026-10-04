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
import kotlin.math.min
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
 * 水彩主题计时器：「芦苇河」原作随专注慢慢显影，过半后芦苇逐支随风摆动。
 *
 * - progress ∈ [0,1]：显影进度。倒计时/番茄专注段 = elapsed/total 的两倍封顶
 *   （前半程走完显影，见 TimerScreen）；
 * - 风场（windActive 由 TimerScreen 决定，当前显影过半后启动）：
 *   底画（reed_base.png，芦苇区已补底）保持静止，
 *   每支芦苇（reed_s0..7.png，用户标注切割出的穗+茎+叶）绕**自己的根部**微旋转，
 *   摆相按行波从左向右扫过（根部钉死、穗尖划弧），远景支减半——动的全是原画真笔触；
 * - completed：走满 → 朱红钤印「心流」淡入；暂停 = 风停（颜料静止）；
 * - ghost：待机幻影（整画低透明度预告，不显影、不钤印、不出数字）。
 */
@Composable
fun ReedPaintingTimer(
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
    val stalkSpecs = remember {
        runCatching {
            val json = org.json.JSONObject(
                context.assets.open("reed_stalks.json").bufferedReader().use { it.readText() },
            )
            val arr = json.getJSONArray("stalks")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StalkSpec(
                    file = o.getString("file"),
                    x = o.getDouble("x").toFloat(), y = o.getDouble("y").toFloat(),
                    sw = o.getDouble("sw").toFloat(), sh = o.getDouble("sh").toFloat(),
                    px = o.getDouble("px").toFloat(), py = o.getDouble("py").toFloat(),
                    rootX = o.getDouble("rootX").toFloat(),
                    distant = o.getBoolean("distant"),
                )
            }
        }.getOrDefault(emptyList())
    }
    val base = remember {
        runCatching {
            context.assets.open("reed_base.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
        }.getOrNull()
    }
    val stalks = remember {
        stalkSpecs.map { s ->
            runCatching {
                context.assets.open(s.file).use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }.getOrNull()
        }
    }
    // 兜底：素材缺失时退回完整原画（无风摆，但画面完整）
    val fallback = remember(base, stalks) {
        if (base == null || stalks.any { it == null }) {
            runCatching {
                context.assets.open("reed_river.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }.getOrNull()
        } else {
            null
        }
    }
    val textMeasurer = rememberTextMeasurer()

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
            sealAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
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

            // 纸底
            drawRect(
                brush = Brush.verticalGradient(listOf(PaperTop, PaperBase, PaperBottom)),
                size = size,
            )

            val img = base ?: fallback
            if (img != null) {
                // 素材画布 934×1685，等比铺满画布
                val scale = min(size.width / img.width, size.height / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                val dx = (size.width - dw) / 2f
                val dy = (size.height - dh) / 2f
                val rect = Rect(Offset(dx, dy), Size(dw, dh))
                val useStalks = base != null && stalks.none { it == null }

                drawIntoCanvas { canvas ->
                    // L1：底画 + 芦苇逐支 + 显影遮罩
                    val l1 = androidx.compose.ui.graphics.Paint().apply {
                        blendMode = BlendMode.SrcOver
                        alpha = if (ghost) 0.08f else 1f
                    }
                    canvas.saveLayer(rect, l1)

                    drawImage(
                        image = img,
                        dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = IntSize(dw.toInt(), dh.toInt()),
                    )

                    if (useStalks) {
                        // 每支芦苇绕根部微旋转：行波按根部 x 从左向右扫，
                        // 主摆（慢，长节奏）+ 细颤（快，小幅度）；远景支减半。
                        val tsec = ts * 0.001f
                        stalkSpecs.forEachIndexed { i, s ->
                            val sprite = stalks[i] ?: return@forEachIndexed
                            val dstX = dx + s.x * dw
                            val dstY = dy + s.y * dh
                            val dstW = s.sw * dw
                            val dstH = s.sh * dw
                            val amp = if (s.distant) 0.5f else 1f
                            val ang = wind * amp * (
                                3.0f * sin(tsec * 0.9f - s.rootX * 6.5f + i * 0.8f) +
                                    0.7f * sin(tsec * 2.3f - s.rootX * 11f + i * 1.9f)
                                )
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

                    // L2：显影遮罩（软边光斑按作画顺序洇开）
                    val l2 = androidx.compose.ui.graphics.Paint().apply { blendMode = BlendMode.DstIn }
                    canvas.saveLayer(rect, l2)
                    sceneBlobs.forEach { b ->
                        val local = clamp01f((progress - b.w0) / (b.w1 - b.w0))
                        if (local <= 0f) return@forEach
                        val grow = 0.30f + 0.70f * easeIO(local)
                        val a = clamp01f(local * 6f)
                        val cc = Offset(rect.left + b.x * dw, rect.top + b.y * dh)
                        val r = b.r * dw * grow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = a),
                                    Color.White.copy(alpha = 0f),
                                ),
                                center = cc,
                                radius = r,
                            ),
                            radius = r,
                            center = cc,
                        )
                    }
                    canvas.restore()
                    canvas.restore()
                }

                // 完成：钤印（朱红方章 + 白文，微旋淡入）
                if (completed && sealAlpha.value > 0f) {
                    drawSeal(rect, sealAlpha.value, sc = 1.15f - 0.15f * easeIO(sealAlpha.value), textMeasurer)
                }
            }
        }

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
private fun DrawScope.drawSeal(
    rect: Rect,
    alpha: Float,
    sc: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    val cx = rect.left + rect.width * 0.82f
    val cy = rect.top + rect.height * 0.10f
    val half = 20.dp.toPx() * sc
    rotate(-6f, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = SealRed.copy(alpha = 0.9f * alpha),
            topLeft = Offset(cx - half, cy - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(7.dp.toPx() * sc),
        )
        val style = TextStyle(color = SealText.copy(alpha = 0.94f * alpha), fontSize = 15.sp)
        val m1 = textMeasurer.measure("心", style)
        val m2 = textMeasurer.measure("流", style)
        drawText(m1, topLeft = Offset(cx - m1.size.width / 2f, cy - half * 0.92f))
        drawText(m2, topLeft = Offset(cx - m2.size.width / 2f, cy + half * 0.92f - m2.size.height))
    }
}

// ── 风场与显影光斑数据 ───────────────────────────────────────
private class StalkSpec(
    val file: String,
    val x: Float, val y: Float,     // 精灵左上角（画布归一化，x 除以宽、y 除以宽保比例）
    val sw: Float, val sh: Float,   // 精灵宽高（宽归一化）
    val px: Float, val py: Float,   // 旋转支点（根部）在精灵内的归一化坐标
    val rootX: Float,               // 根部 x（画布归一化）→ 行波相位
    val distant: Boolean,           // 远景穗：摆幅减半
)

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
