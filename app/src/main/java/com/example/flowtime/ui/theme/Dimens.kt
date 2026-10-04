package com.example.flowtime.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸令牌：一处定义，处处一致。
 *
 * 收紧基准（对齐 Material 3 + Android 无障碍下限）：
 * - 按钮高度：M3 标准 40dp，这里取 48dp —— 因为 Android 的最小触控目标就是 48dp，
 *   再小会点不准，所以 48dp 是"能收紧到的下限"；
 * - 两行列表项：M3 标准 72dp（= 行内边距 14 + 图标块 44 + 14）；
 * - 屏幕左右边距：M3 建议 16dp，这里取 20dp 保留水彩的呼吸感；
 * - 相邻可点元素间距：不小于 8dp（M3 触控间距要求）。
 *
 * 改造前的老值是 24/20/18/16/14/12 混用（屏幕边距 24 vs 20、卡片内边距 20 vs 16、
 * 按钮高度 56 vs 52），现在统一走这里，以后要调只改这一个文件。
 */
object Dimens {
    // ---- 屏幕级 ----
    /** 屏幕左右边距 */
    val ScreenPadding = 20.dp
    /** 大屏内容最大宽度（居中） */
    val ContentMaxWidth = 560.dp
    /** 页面顶部留白 */
    val ScreenTopGap = 20.dp

    // ---- 卡片 ----
    /** 卡片圆角（rounded-3xl 档：玻璃卡的大圆角，直角与小圆角都会破坏玻璃感） */
    val CardRadius = 24.dp
    /** 大卡片内容内边距 */
    val CardPadding = 16.dp
    /** 紧凑卡片（任务网格块）内边距 */
    val CardPaddingTight = 14.dp
    /** 任务网格块高度（原 176dp 偏高，收一档后两列比例更舒展） */
    val GridCardHeight = 152.dp
    /** 列表行/紧凑卡片内边距（与图标块配合得到 72dp 两行行高） */
    val RowPadding = 14.dp
    /** 同组卡片之间的间距 */
    val CardGap = 12.dp
    /** 前置图标/色块与文字之间的间距 */
    val IconTextGap = 12.dp
    /** 分组之间的间距 */
    val SectionGap = 16.dp
    /** 列表底部留白（避免最后一项贴住底栏） */
    val ListBottomGap = 20.dp

    // ---- 图标块 / 头像 ----
    /** 列表行前置图标块（原 48dp → 44dp，行高才落到 72dp） */
    val IconChip = 44.dp
    /** 图标块内图形 */
    val IconChipGlyph = 22.dp
    /** 任务卡上的小图标块 */
    val IconChipSmall = 36.dp
    /** 头像（与图标块同尺寸，视觉成一行） */
    val Avatar = 44.dp

    // ---- 按钮 ----
    /** 主按钮/次按钮高度：不低于 48dp（触控下限） */
    val ButtonHeight = 48.dp
    /** 首页主 CTA 略高一点，保持"要按下它"的视觉分量 */
    val ButtonHeightHero = 52.dp
    /** 主按钮字号（M3 labelLarge 是 14sp，这里 16sp 保留可读性） */
    val ButtonLabel = 16
    /** 次按钮/对话框中按钮字号 */
    val ButtonLabelSmall = 15
}
