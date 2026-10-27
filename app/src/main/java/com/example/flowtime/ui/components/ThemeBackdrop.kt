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
 * 粒子系统用深蓝黑底 + 漂浮粒子网（粒子 + 邻近连线），
 * 宫廷御制用玄青底 + 孔雀青/金雾两枚柔光斑。
 * 单色极简走 else：纸底就够，不加场景层。
 *
 * 本层由 `MainActivity` 在 **NavHost 外全局挂一次**（2026-10-16 起）：所有页面共用同一张
 * 连续背景，切页时粒子不重置、光球不重启。所以它不再接收任何页面级参数。
 *
 * @param keepOutCenterYFraction 透传给粒子网的中心禁区圆心纵向比例；**null = 不挖禁区**。
 *        ⚠️ 当前**无调用方**：背景全局化后只有一个值可用，而用户要求三页背景完全一致，
 *        所以统一不挖。保留形参是给以后"某个页面确实需要避让"留的开关，不是死分支。
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
        ThemeMode.PARTICLES -> ParticleNetBackdrop(modifier, keepOutCenterYFraction)
        ThemeMode.IMPERIAL -> ImperialCourtScene(modifier)
        else -> {}
    }
}
