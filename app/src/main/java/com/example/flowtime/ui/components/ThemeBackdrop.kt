package com.example.flowtime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 页面背景场景：按当前主题选择 ——
 * 玻璃拟态用夜航光斑场景（玻璃才有东西可借光），
 * 水彩画风用水彩晕染底，
 * 手绘涂鸦用笔记本纸页（横线 + 红边线 + 涂鸦），
 * 现代渐变用深紫夜底 + 三团漂移柔光球，
 * 粒子系统用深蓝黑底 + 漂浮粒子网（粒子 + 邻近连线）。
 * 单色极简走 else：纸底就够，不加场景层。
 *
 * @param keepOutCenterYFraction 透传给粒子网的中心禁区圆心纵向比例；**null = 不挖禁区**。
 *        只有计时页的中心压着大号时间数字，才需要"别让粒子压上去"—— 其余页面挖了
 *        反而是凭空在 38% 高度处压暗一团雾（而真正的内容并不在那儿）。故由调用方给。
 */
@Composable
fun ThemeBackdrop(
    modifier: Modifier = Modifier,
    keepOutCenterYFraction: Float? = null,
) {
    when (ActivePalette.mode) {
        ThemeMode.NOCTURNE -> NocturneScene(modifier)
        ThemeMode.DOODLE -> DoodleBackdrop(modifier)
        ThemeMode.WATERCOLOR -> WatercolorBloom(modifier)
        ThemeMode.GRADIENT -> GradientBackdrop(modifier)
        ThemeMode.PARTICLES -> ParticleNetBackdrop(modifier, keepOutCenterYFraction)
        else -> {}
    }
}
