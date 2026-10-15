package com.example.flowtime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 页面背景场景：按当前主题选择 ——
 * 玻璃拟态用夜航光斑场景（玻璃才有东西可借光），
 * 水彩画风用水彩晕染底，
 * 手绘涂鸦用笔记本纸页（横线 + 红边线 + 涂鸦）。
 */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier) {
    when (ActivePalette.mode) {
        ThemeMode.NOCTURNE -> NocturneScene(modifier)
        ThemeMode.DOODLE -> DoodleBackdrop(modifier)
        ThemeMode.WATERCOLOR -> WatercolorBloom(modifier)
        // 水墨：纯宣纸留白（计白当黑，留白即设计），走 bgPrimary 纯色底
        ThemeMode.INKWASH -> {}
        else -> {}
    }
}
