package com.example.flowtime.ui.theme

import androidx.compose.ui.graphics.Color

// ── 水彩画风调色板 ─────────────────────────────────────────────
// 纸张色底 + 蓝灰主色 + 四种半透明水彩色（蜜桃/青绿/玫瑰/沙）
// 规则：不用纯黑、不用高饱和色、一切颜色以半透明方式叠加

/** 水彩色（以半透明方式使用） */
val WatercolorInk = Color(0xFF4A6FA5)    // 蓝灰：文字与主要元素
val WatercolorPeach = Color(0xFFE8A87C)  // 蜜桃
val WatercolorTeal = Color(0xFF85CDCA)   // 青绿
val WatercolorRose = Color(0xFFC38D94)   // 玫瑰
val WatercolorSand = Color(0xFFD4A373)   // 沙色

/** 纸张色 */
val PaperBase = Color(0xFFFAF8F5)
val PaperSoft = Color(0xFFF0EBE3)
val PaperElevated = Color(0xFFFCFAF7)

// 浅色主题
val BgPrimary = PaperBase                  // 温暖纸张色背景
val Primary = WatercolorInk                // 蓝灰主色
val PrimaryDark = Color(0xFF3C5A87)
val PrimaryLight = Color(0xFF7A98C4)
val AccentGreen = WatercolorTeal           // 青绿（沿用旧名，值改为水彩色）
val CardGreenLight = Color(0xFFDDEDEC)     // 青绿 20%
val CardOrange = Color(0xFFF4D9C3)         // 蜜桃 30%
val CardCream = PaperSoft                  // 纸张辅色
val MutedSurface = Color(0xFFF3EFE9)
val Gold = WatercolorSand
val TextPrimary = Color(0xFF3A3A3A)        // 正文主色（非纯黑）
val TextSecondary = WatercolorInk          // 正文辅色 = 蓝灰
val TextPlaceholder = Color(0xFF8A8A8A)    // 弱化色
val DividerColor = Color(0x334A6FA5)       // 蓝灰 20%
val BorderColor = Color(0x334A6FA5)        // 细描边：蓝灰 20%
val SurfaceCard = PaperElevated
val ErrorRed = WatercolorRose              // 玫瑰（柔和警示，非刺眼红）
val WarningAmber = WatercolorSand
val SuccessGreen = WatercolorTeal
val DisabledGray = Color(0xFFC6C0B7)

// 深色主题（暖深纸色，禁止纯黑）
val BgPrimaryNight = Color(0xFF23211F)
val PrimaryNight = Color(0xFFA9C0E0)
val SurfaceNight = Color(0xFF2C2A27)
val TextPrimaryNight = Color(0xFFEFEBE4)
val TextSecondaryNight = Color(0xFFB6B0A6)
val DividerNight = Color(0x33EFEBE4)

// 任务卡片可选底色：水彩淡彩 16 色（低饱和、纸上调），深色配白字，对比度 ≥4.5:1
val TaskCardPalette = listOf(
    Color(0xFFE1EAF5) to TextPrimary,   // 蓝灰淡
    WatercolorInk to Color.White,       // 蓝灰深
    Color(0xFFF6E0CE) to TextPrimary,   // 蜜桃淡
    Color(0xFFDFF0EE) to TextPrimary,   // 青绿淡
    Color(0xFFF4E1E3) to TextPrimary,   // 玫瑰淡
    Color(0xFFF2E7D8) to TextPrimary,   // 沙色淡
    Color(0xFFE6EFE7) to TextPrimary,   // 苔绿淡
    Color(0xFFEDE7F2) to TextPrimary,   // 淡紫
    Color(0xFFF7EBDD) to TextPrimary,   // 杏仁淡
    Color(0xFFE3EEF0) to TextPrimary,   // 湖水淡
    Color(0xFFF1E6EA) to TextPrimary,   // 藕荷淡
    Color(0xFFE5E9F2) to TextPrimary,   // 灰蓝淡
    Color(0xFFF0E9DF) to TextPrimary,   // 奶茶淡
    Color(0xFF3F6E8C) to Color.White,   // 深青
    Color(0xFF3C4C7C) to Color.White,   // 靛蓝
    Color(0xFFA8724A) to Color.White,   // 焦糖
)
