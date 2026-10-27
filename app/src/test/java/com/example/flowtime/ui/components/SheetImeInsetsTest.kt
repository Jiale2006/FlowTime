package com.example.flowtime.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语音输入（全屏/超高输入法面板）时弹层保底高度的钳制逻辑。
 * 背景见 [KeepSheetAboveIme] 的注释。
 */
class SheetImeInsetsTest {

    @Test
    fun `普通键盘高度原样返回`() {
        // 2400px 高的窗口、240dp(=720px@480dpi) 保底、键盘 900px：够用，不动
        assertEquals(900, clampImeInsetPx(windowHeightPx = 2400, imeBottomPx = 900, minVisiblePx = 720))
    }

    @Test
    fun `全屏语音面板被钳到保底高度`() {
        // 语音面板 2400px 高（整屏）：最多吃掉 2400-720=1680，留 720px 给弹层
        assertEquals(1680, clampImeInsetPx(windowHeightPx = 2400, imeBottomPx = 2400, minVisiblePx = 720))
    }

    @Test
    fun `保底高度大于窗口时不产生负数 inset`() {
        assertEquals(0, clampImeInsetPx(windowHeightPx = 600, imeBottomPx = 600, minVisiblePx = 720))
    }

    @Test
    fun `窗口高度未知时不钳制`() {
        assertEquals(1200, clampImeInsetPx(windowHeightPx = 0, imeBottomPx = 1200, minVisiblePx = 720))
    }

    @Test
    fun `没有输入法时不钳制`() {
        assertEquals(0, clampImeInsetPx(windowHeightPx = 2400, imeBottomPx = 0, minVisiblePx = 720))
    }
}
