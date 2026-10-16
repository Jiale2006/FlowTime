package com.example.flowtime.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.AllPalettes
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 热力图 / 月历格子里那枚日号，在三套主题下都必须看得清。
 *
 * 为什么值得写测试：色阶是半透明的，三套主题的卡片底色明暗**相反**
 * （纸主题米白、夜航深钢蓝、玻璃的 cardCream 只有 3% 白），
 * 字色如果按印象写死（比如直接用 textPrimary，夜航下是近白），
 * 必然在其中一套主题上糊掉——而这种问题光看代码看不出来，只有真机切主题才发现。
 *
 * 判据用 WCAG 的对比度公式，正文级 4.5:1。
 */
class HeatContrastTest {

    private val original = ActivePalette

    @After
    fun restore() {
        ActivePalette = original
    }

    /** WCAG 相对亮度对比度 */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** 覆盖五档色阶：0（无记录）/ 10 / 45 / 75 / 120 分钟 */
    private val samples = listOf(0, 10, 45, 75, 120)

    @Test
    fun `三套主题下每个色档的日号都够清楚`() {
        AllPalettes.forEach { palette ->
            ActivePalette = palette
            samples.forEach { minutes ->
                val fill = heatColorOf(minutes)
                val ratio = contrast(inkOn(fill), cellSurface(fill))
                assertTrue(
                    "${palette.label} 主题、${minutes} 分钟的格子：日号对比度只有 " +
                        "%.2f:1（要求 ≥ 4.5）".format(ratio),
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `格子底色本身深浅有别，色阶才有意义`() {
        AllPalettes.forEach { palette ->
            ActivePalette = palette
            val lights = samples.map { cellSurface(heatColorOf(it)).luminance() }
            // 无记录格与最深格必须明显不同，否则"热力图"读不出趋势。
            // 注意用 abs：**暗主题下色阶是"越专注越亮"**（深底 + 亮色），
            // 纸主题才是"越专注越深"，方向本来就相反。
            assertTrue(
                "${palette.label} 主题：最浅格与最深格亮度太接近（${lights.first()} vs ${lights.last()}）",
                kotlin.math.abs(lights.first() - lights.last()) > 0.2f,
            )
        }
    }

    @Test
    fun `无记录格的字色跟着底色走 深底用浅字 纸底用深字`() {
        AllPalettes.forEach { palette ->
            ActivePalette = palette
            val inkLum = inkOn(heatColorOf(0)).luminance()
            // 判据不写死主题名单，而是按**页面底色本身的明暗**分派。
            // 原来那条断言的前提是"夜航是唯一深底主题"—— 深底主题涨到 3 个
            //（夜航 / 现代渐变 / 粒子系统）之后，白名单式写法必然误报：
            // 深底上无记录格**注定**用白字，而白字恰恰是对的。
            if (palette.bgPrimary.luminance() < 0.5f) {
                assertTrue(
                    "${palette.label} 是深底主题，无记录格应该用浅色字（实得 luminance=$inkLum）",
                    inkLum > 0.5f,
                )
            } else {
                assertTrue(
                    "${palette.label} 是浅底主题，无记录格应该用深色字（实得 luminance=$inkLum）",
                    inkLum < 0.5f,
                )
            }
        }
    }
}
