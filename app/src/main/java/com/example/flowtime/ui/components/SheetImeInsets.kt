package com.example.flowtime.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 输入法 inset 的钳制：窗口高 [windowHeightPx]，输入法自报高 [imeBottomPx]，
 * 最多允许它吃掉「窗口高 - [minVisiblePx]」，剩下的高度留给浮层。
 *
 * 窗口高度未知（布局之前派发的那一次）时原样返回 —— 那时浮层还没测量，既不必要也不能钳制。
 */
internal fun clampImeInsetPx(windowHeightPx: Int, imeBottomPx: Int, minVisiblePx: Int): Int {
    if (windowHeightPx <= 0 || imeBottomPx <= 0) return imeBottomPx
    val limit = (windowHeightPx - minVisiblePx).coerceAtLeast(0)
    return if (imeBottomPx > limit) limit else imeBottomPx
}

/**
 * 给当前浮层窗口的输入法 inset 上一层钳制，保证浮层至少还剩 [minVisible] 高。
 *
 * 解决的是 M3 `ModalBottomSheet` 在**输入法高度接近窗口高度**时「先消失、再弹回来」的问题：
 *
 * M3 的弹层窗口是「整块窗口 `imePadding()` + Surface 撑满」，锚点按 `fullHeight = 剩余高度`
 * 计算，其中 `Expanded at max(0, fullHeight - 弹层高度)` 只有在弹层量到的高度不为 0 时才存在。
 * 搜狗语音输入是把键盘换成一块**很高的语音面板**（常常接近全屏），此时可用高度被压到 0 →
 * 弹层量出 0 高 → 锚点里不再有 Expanded → M3 把目标值判成 `Hidden`（弹层"啪"地消失）；
 * 语音面板关掉、可用高度恢复后，`if (sheetState.hasExpandedState) LaunchedEffect(sheetState) {
 * sheetState.show() }` 又跑一遍 → 弹层再滑上来。观感就是「开始识别时掉下去、识别完弹上来」，
 * 而且输入法连接没断（识别出的文字照样落进输入框）。
 *
 * 这里在浮层窗口的 decorView 上截一层 insets 派发，把 ime 高度钳到
 * 「窗口高 - 保底高度」，锚点里永远留得住 Expanded：弹层只会随键盘上移，不会再消失。
 * 普通键盘（约 40% 屏高）达不到钳制线，行为与之前完全一致。
 *
 * 只在 API 30+ 生效（更低的版本没法把 ime 类型写回 `WindowInsetsCompat`）；
 * 拿不到窗口或系统太低时静默不生效，浮层照常显示。
 */
@Composable
fun KeepSheetAboveIme(minVisible: Dp = 240.dp) {
    val view = LocalView.current
    val density = LocalDensity.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val minVisiblePx = with(density) { minVisible.roundToPx() }

    DisposableEffect(window, minVisiblePx) {
        val decor = window?.decorView
        if (decor != null) {
            ViewCompat.setOnApplyWindowInsetsListener(decor) { v, insets ->
                clampImeInsets(v, insets, minVisiblePx)
            }
        }
        onDispose {
            // decorView 上原本没有监听（Compose 把监听装在自己的 AndroidComposeView 上），
            // 直接清掉就行
            if (decor != null) ViewCompat.setOnApplyWindowInsetsListener(decor, null)
        }
    }
}

private fun clampImeInsets(
    view: View,
    insets: WindowInsetsCompat,
    minVisiblePx: Int,
): WindowInsetsCompat {
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
    val clampedBottom = clampImeInsetPx(view.height, ime.bottom, minVisiblePx)
    if (clampedBottom == ime.bottom) return insets
    return WindowInsetsCompat.Builder(insets)
        .setInsets(
            WindowInsetsCompat.Type.ime(),
            Insets.of(ime.left, ime.top, ime.right, clampedBottom),
        )
        .build()
}
