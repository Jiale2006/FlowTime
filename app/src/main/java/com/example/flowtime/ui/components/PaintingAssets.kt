package com.example.flowtime.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 水彩主题的「芦苇河」素材：9 张 PNG + 一份杆位表。
 *
 * 🔴 2026-10-25 从 [ReedPaintingTimer] 里抽出来，原因是**切主题会卡一下**：
 * 那 9 张图（底图 934×1685 解出来 6.3 MB，加 8 张芦苇精灵约 3 MB）原来是在
 * 组合期用 `remember { BitmapFactory.decodeStream(...) }` **在主线程**解的，
 * 于是「设置 → 切到水彩」的那一刻主线程被占住，实测最慢一帧 150 ms（掉帧可见）。
 *
 * 现在：解码全部走 [Dispatchers.IO]，结果缓存进 [PaintingAssetsLoader]，
 * 应用启动时预热一次 —— 用户真去切主题时素材早在内存里，切换不再有这一下卡顿。
 *
 * 缓存刻意不做回收：总共约 10 MB，按住不动的收益是不用重复解；主题切走后
 * 位图仍被持有，但这是静态素材，不会增长。
 */
internal class StalkSpec(
    val file: String,
    val x: Float, val y: Float,     // 精灵左上角（画布归一化，x 除以宽、y 除以宽保比例）
    val sw: Float, val sh: Float,   // 精灵宽高（宽归一化）
    val px: Float, val py: Float,   // 旋转支点（根部）在精灵内的归一化坐标
    val rootX: Float,               // 根部 x（画布归一化）→ 行波相位
    val distant: Boolean,           // 远景穗：摆幅减半
)

internal class PaintingAssets(
    val specs: List<StalkSpec>,
    val base: ImageBitmap?,
    /** 与 [specs] 一一对应；任一为 null 时调用方退回整幅原画 */
    val stalks: List<ImageBitmap?>,
    /** 素材缺失时的兜底原画（无风摆，但画面完整）；正常情况下为 null */
    val fallback: ImageBitmap?,
    /** 水面的动效作用域遮罩 */
    val waterMask: ImageBitmap?,
)

internal object PaintingAssetsLoader {

    @Volatile
    private var cached: PaintingAssets? = null

    private val lock = Mutex()

    /** 组合期先用它拿现成的，拿不到再 suspend 等解码 */
    fun peek(): PaintingAssets? = cached

    /**
     * 应用启动时预热：切主题时素材已在内存。
     * 失败不抛（缺素材有自己的兜底路径）。
     */
    suspend fun warmUp(context: Context) {
        runCatching { load(context) }
    }

    suspend fun load(context: Context): PaintingAssets {
        cached?.let { return it }
        return lock.withLock {
            cached ?: decode(context.applicationContext).also { cached = it }
        }
    }

    private suspend fun decode(context: Context): PaintingAssets = withContext(Dispatchers.IO) {
        val specs = runCatching { readSpecs(context) }.getOrDefault(emptyList())
        val base = decodeAsset(context, "reed_base.png")
        val stalks = specs.map { decodeAsset(context, it.file) }
        // 兜底只在真缺素材时才解（它和底图一样大，不解就是白省 6 MB）
        val fallback = if (base == null || stalks.any { it == null }) {
            decodeAsset(context, "reed_river.png")
        } else {
            null
        }
        PaintingAssets(
            specs = specs,
            base = base,
            stalks = stalks,
            fallback = fallback,
            waterMask = decodeAsset(context, "reed_water_mask.png"),
        )
    }

    private fun decodeAsset(context: Context, name: String): ImageBitmap? = runCatching {
        context.assets.open(name).use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }.getOrNull()

    private fun readSpecs(context: Context): List<StalkSpec> {
        val json = JSONObject(
            context.assets.open("reed_stalks.json").bufferedReader().use { it.readText() },
        )
        val arr = json.getJSONArray("stalks")
        return (0 until arr.length()).map { i ->
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
    }
}
