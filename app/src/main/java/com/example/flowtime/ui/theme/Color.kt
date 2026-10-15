package com.example.flowtime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

// ── 多主题体系 ─────────────────────────────────────────────────
// 全站界面只引用本文件的 token（BgPrimary、Primary……），token 是
// 「当前调色板 ActivePalette」的读取器；设置页切换主题时整体换装。
// 三套起步主题：水彩画风 / 玻璃拟态 / 手绘涂鸦；后追加单色极简、水墨画风，共五套。
// 形状与装饰也属于主题语言（卡片圆角、胶囊 chip、虚线边框、横幅笔刷），
// 一并放进 AppPalette，组件里不允许再写死 RoundedCornerShape。

/** 可切换的主题模式 */
enum class ThemeMode { WATERCOLOR, NOCTURNE, DOODLE, MONO, INKWASH }

/** 一套主题的全部 token：颜色 + 形状 + 装饰 */
interface AppPalette {
    val mode: ThemeMode

    // ---- 选择器展示 ----
    /** 选择界面里显示的名称 / 一句话风格 */
    val label: String
    val tagline: String
    /** 三个预览色点 */
    val previewDots: List<Color>

    // ---- 颜色 ----
    val bgPrimary: Color
    val primary: Color
    val primaryDark: Color
    val primaryLight: Color
    val accentGreen: Color
    val cardGreenLight: Color
    val cardOrange: Color
    val cardCream: Color
    val mutedSurface: Color
    val gold: Color
    val textPrimary: Color
    val textSecondary: Color
    val textPlaceholder: Color
    val dividerColor: Color
    val borderColor: Color
    val surfaceCard: Color
    /** 弹窗容器色（比卡片更实一层，保证可读性） */
    val dialogContainer: Color
    val errorRed: Color
    val warningAmber: Color
    val successGreen: Color
    val disabledGray: Color
    /** 任务卡片可选底色（底色 → 文字色，对比度 ≥4.5:1） */
    val taskCardPalette: List<Pair<Color, Color>>
    /** 底部导航条底色 / 未选中项色 */
    val navContainer: Color
    val navItemUnselected: Color
    /** 卡片阴影墨色与透明度（水彩=晕染 blur，夜航=近黑深影，涂鸦=硬偏移不用 blur） */
    val shadowInk: Color
    val shadowAmbientAlpha: Float
    val shadowSpotAlpha: Float
    /** 品牌辅色（装饰点、徽章、烟花共用） */
    val ink: Color
    val peach: Color
    val teal: Color
    val rose: Color
    val sand: Color

    // ---- 形状 ----
    /** 大卡（ProCard、横幅、弹层内卡片） */
    val cardShape: Shape
    /** 中等贴片（概览小卡、任务卡、进度条轨道） */
    val tileShape: Shape
    /** 小徽章（图标底块、分类标注） */
    val badgeShape: Shape
    /** 按钮 */
    val buttonShape: Shape
    /** 胶囊 chip（范围筛选等；涂鸦主题退化为小圆角——禁 rounded-full） */
    val pillShape: Shape

    // ---- 装饰 ----
    /** 手绘风虚线描边（涂鸦主题 true，其余 false 走实线） */
    val dashedBorders: Boolean
    /** 主题横幅（设置页「专注设置」高亮卡）的填充笔刷 */
    val bannerBrush: Brush
}

// ── 水彩画风（第一版）──────────────────────────────────────────
// 纸张色底 + 蓝灰主色 + 四种半透明水彩色（蜜桃/青绿/玫瑰/沙）
// 规则：不用纯黑、不用高饱和色、一切颜色以半透明方式叠加
object WatercolorPalette : AppPalette {
    override val mode = ThemeMode.WATERCOLOR
    override val label = "水彩画风"
    override val tagline = "纸底 · 蓝灰墨"
    override val previewDots = listOf(Color(0xFFFAF8F5), Color(0xFF4A6FA5), Color(0xFF85CDCA))

    override val bgPrimary = Color(0xFFFAF8F5)
    override val primary = Color(0xFF4A6FA5)
    override val primaryDark = Color(0xFF3C5A87)
    override val primaryLight = Color(0xFF7A98C4)
    override val accentGreen = Color(0xFF85CDCA)
    override val cardGreenLight = Color(0xFFDDEDEC)
    override val cardOrange = Color(0xFFF4D9C3)
    override val cardCream = Color(0xFFF0EBE3)
    override val mutedSurface = Color(0xFFF3EFE9)
    override val gold = Color(0xFFD4A373)
    override val textPrimary = Color(0xFF3A3A3A)
    override val textSecondary = Color(0xFF4A6FA5)
    override val textPlaceholder = Color(0xFF8A8A8A)
    override val dividerColor = Color(0x334A6FA5)
    override val borderColor = Color(0x334A6FA5)
    override val surfaceCard = Color(0xFFFCFAF7)
    override val dialogContainer = Color(0xFFFCFAF7)
    override val errorRed = Color(0xFFC38D94)
    override val warningAmber = Color(0xFFD4A373)
    override val successGreen = Color(0xFF85CDCA)
    override val disabledGray = Color(0xFFC6C0B7)
    override val taskCardPalette = listOf(
        Color(0xFFE1EAF5) to textPrimary,   // 蓝灰淡
        primary to Color.White,             // 蓝灰深
        Color(0xFFF6E0CE) to textPrimary,   // 蜜桃淡
        Color(0xFFDFF0EE) to textPrimary,   // 青绿淡
        Color(0xFFF4E1E3) to textPrimary,   // 玫瑰淡
        Color(0xFFF2E7D8) to textPrimary,   // 沙色淡
        Color(0xFFE6EFE7) to textPrimary,   // 苔绿淡
        Color(0xFFEDE7F2) to textPrimary,   // 淡紫
        Color(0xFFF7EBDD) to textPrimary,   // 杏仁淡
        Color(0xFFE3EEF0) to textPrimary,   // 湖水淡
        Color(0xFFF1E6EA) to textPrimary,   // 藕荷淡
        Color(0xFFE5E9F2) to textPrimary,   // 灰蓝淡
        Color(0xFFF0E9DF) to textPrimary,   // 奶茶淡
        Color(0xFF3F6E8C) to Color.White,   // 深青
        Color(0xFF3C4C7C) to Color.White,   // 靛蓝
        Color(0xFFA8724A) to Color.White,   // 焦糖
    )
    override val navContainer = Color(0xFFFCFAF7)
    override val navItemUnselected = Color(0xFF8A8A8A)
    override val shadowInk = primary
    override val shadowAmbientAlpha = 0.35f
    override val shadowSpotAlpha = 0.28f
    override val ink = primary
    override val peach = Color(0xFFE8A87C)
    override val teal = accentGreen
    override val rose = errorRed
    override val sand = gold

    override val cardShape = RoundedCornerShape(20.dp)
    override val tileShape = RoundedCornerShape(16.dp)
    override val badgeShape = RoundedCornerShape(12.dp)
    override val buttonShape = RoundedCornerShape(16.dp)
    override val pillShape = RoundedCornerShape(50)
    override val dashedBorders = false
    override val bannerBrush = Brush.horizontalGradient(listOf(primary, primaryDark))
}

// ── 玻璃拟态 / Nocturne（第二版）───────────────────────────────
// 深墨夜景底 + 无色玻璃面板 + 唯一强调色香槟金。
// 规则：玻璃无色（白 5%~12% 半透明），颜色全部由夜景场景承载；
//       光有方向（顶边受光高光 / 底边背光暗缘）；禁止紫粉渐变、
//       禁止纯黑纯白面板、禁止给玻璃本身上色。

/** 夜景场景色（背景拥有全部颜色；仅夜航场景层直接使用） */
val NightDeep = Color(0xFF060A13)
val NightBase = Color(0xFF0B1322)
val SteelLift = Color(0xFF16233A)
val MoonSteel = Color(0xFF33517A)
val Moonlight = Color(0xFF7C9CC4)

/** 玻璃表面（永远无色；仅夜航玻璃层直接使用） */
val GlassFill = Color(0x08FFFFFF)
val GlassFillHigh = Color(0x1FFFFFFF)
val GlassBorder = Color(0x26FFFFFF)
val GlassBorderHigh = Color(0x4DFFFFFF)
val GlassInnerLight = Color(0x0DFFFFFF)
val GlassBottomShade = Color(0x59020610)

/** 唯一强调色：香槟金（只用于主 CTA、关键数字、高亮文字） */
val Champagne = Color(0xFFE4B863)
val ChampagneBright = Color(0xFFF3DCA8)
val ChampagneTint = Color(0x33E4B863)
val OnChampagne = Color(0xFF221A0E)

object NocturnePalette : AppPalette {
    override val mode = ThemeMode.NOCTURNE
    override val label = "玻璃拟态"
    override val tagline = "深夜 · 香槟金"
    override val previewDots = listOf(NightBase, Champagne, GlassBorder)

    override val bgPrimary = NightBase
    override val primary = Champagne
    override val primaryDark = MoonSteel
    override val primaryLight = ChampagneBright
    override val accentGreen = Moonlight
    override val cardGreenLight = ChampagneTint
    override val cardOrange = Color(0x2EE4B863)
    override val cardCream = GlassFill
    override val mutedSurface = Color(0x08FFFFFF)
    override val gold = Color(0xFFA8823F)
    override val textPrimary = Color(0xFFF2F5FA)
    override val textSecondary = Color(0xFFB7C2D4)
    override val textPlaceholder = Color(0xFF7E8BA0)
    override val dividerColor = GlassBorder
    override val borderColor = GlassBorder
    // 内容卡 = 深色"标准材质"（近实心钢蓝）而非半透明白：Android 端没有逐卡 backdrop blur，
    // 白 8% 无模糊就是提示词自己禁的"实心假玻璃色块"；Apple 也把玻璃限定在浮层控件，
    // 内容层用标准材质（方向性顶光/底暗缘仍由 ProCard 绘制）。弹窗/浮层才吃真窗口模糊。
    override val surfaceCard = Color(0xE616233A)
    override val dialogContainer = Color(0xFF1B2A44)
    override val errorRed = Color(0xFFD98E96)
    override val warningAmber = Color(0xFFD9A45B)
    override val successGreen = Color(0xFF7FB69E)
    override val disabledGray = Color(0xFF55617A)
    override val taskCardPalette = listOf(
        Color(0xFF374F72) to Color.White,   // 钢蓝
        Color(0xFF2E5A55) to Color.White,   // 深青
        Color(0xFF6E4A50) to Color.White,   // 酒红
        Color(0xFF565076) to Color.White,   // 暮紫
        Color(0xFF3E5C46) to Color.White,   // 苔绿
        Color(0xFF2F5866) to Color.White,   // 湖水
        Color(0xFF46536B) to Color.White,   // 灰蓝
        Color(0xFF3A4A74) to Color.White,   // 靛蓝
        Color(0xFF7A5A38) to Color.White,   // 古铜
        Color(0xFF8A6E46) to Color.White,   // 暗沙
        Color(0xFF77614A) to Color.White,   // 奶茶深
        Color(0xFF6A4A62) to Color.White,   // 藕荷深
        Color(0xFF9A6B3E) to Color.White,   // 焦糖
        Champagne to OnChampagne,           // 香槟（深字）
        Color(0xFF2F5578) to Color.White,   // 深钢
        Color(0xFF8A5A34) to Color.White,   // 杏仁深
    )
    override val navContainer = Color(0xD916233A)
    override val navItemUnselected = Color(0x80FFFFFF)
    override val shadowInk = NightDeep
    override val shadowAmbientAlpha = 0.35f
    override val shadowSpotAlpha = 0.32f
    override val ink = Moonlight
    override val peach = Color(0xFFC98F5E)
    override val teal = Color(0xFF6FB3A6)
    override val rose = Color(0xFFC9848F)
    override val sand = Champagne

    override val cardShape = RoundedCornerShape(24.dp)
    override val tileShape = RoundedCornerShape(16.dp)
    override val badgeShape = RoundedCornerShape(12.dp)
    override val buttonShape = RoundedCornerShape(16.dp)
    override val pillShape = RoundedCornerShape(50)
    override val dashedBorders = false
    override val bannerBrush = Brush.horizontalGradient(listOf(primary, primaryDark))
}

// ── 手绘涂鸦 / Hand-Drawn Doodle（第三版）──────────────────────
// 奶白笔记本纸 + 墨黑手绘线条 + 红/蓝绿/黄三支标记笔。
// 规则：虚线边框模拟手绘笔触；小圆角（禁 rounded-lg 及以上、禁直角）；
//       硬边偏移阴影（禁 blur 阴影）；扁平标记笔色（禁一切渐变）；
//       笔记本横线 + 红色页边线 + 胶带/涂鸦装饰。

/** 标记笔三色 + 纸张（涂鸦场景层与强调色共用） */
val DoodleInk = Color(0xFF2C2C2C)
val DoodlePaper = Color(0xFFFFFEF5)
val DoodlePaperSoft = Color(0xFFF8F7F0)
val DoodleRed = Color(0xFFFF6B6B)
val DoodleTeal = Color(0xFF4ECDC4)
val DoodleYellow = Color(0xFFFFD93D)

object DoodlePalette : AppPalette {
    override val mode = ThemeMode.DOODLE
    override val label = "手绘涂鸦"
    override val tagline = "纸页 · 标记笔"
    override val previewDots = listOf(DoodlePaper, DoodleRed, DoodleTeal)

    override val bgPrimary = DoodlePaper
    override val primary = DoodleInk                       // 主按钮 = 墨黑底 + 纸色字
    override val primaryDark = Color(0xFF1F1F1F)
    override val primaryLight = DoodleRed
    override val accentGreen = DoodleTeal
    override val cardGreenLight = DoodleYellow             // 选中态 = 黄色标记笔划重点
    override val cardOrange = DoodleYellow
    override val cardCream = DoodlePaperSoft
    override val mutedSurface = DoodlePaperSoft
    override val gold = DoodleRed
    override val textPrimary = DoodleInk
    override val textSecondary = Color(0xB32C2C2C)         // 墨 70%
    override val textPlaceholder = Color(0x732C2C2C)       // 墨 45%
    override val dividerColor = Color(0x592C2C2C)
    override val borderColor = DoodleInk
    override val surfaceCard = DoodlePaper
    override val dialogContainer = DoodlePaper
    override val errorRed = DoodleRed
    override val warningAmber = DoodleYellow
    override val successGreen = DoodleTeal
    override val disabledGray = Color(0xFFB9B8A8)
    override val taskCardPalette = listOf(
        Color(0xFFFFE1E1) to textPrimary,   // 红笔淡
        DoodleTeal to textPrimary,          // 蓝绿笔
        DoodleYellow to textPrimary,        // 黄笔
        DoodleRed to Color.White,           // 红笔（白字）
        Color(0xFFFFFDF0) to textPrimary,   // 纸白
        DoodlePaperSoft to textPrimary,     // 辅纸色
        Color(0xFFFFE9C7) to textPrimary,   // 蜜桃纸
        Color(0xFFD9F2EF) to textPrimary,   // 蓝绿淡
        Color(0xFFFDE8EC) to textPrimary,   // 粉纸
        Color(0xFFFFF9DB) to textPrimary,   // 淡黄
        Color(0xFFE8F6F4) to textPrimary,   // 青纸
        Color(0xFFF5EFE3) to textPrimary,   // 牛皮纸
        Color(0xFFE3EDF9) to textPrimary,   // 蓝格纸
        Color(0xFFDDEEDD) to textPrimary,   // 浅绿纸
        DoodleInk to Color.White,           // 墨卡（白字）
        Color(0xFFB9E4E0) to textPrimary,   // 深青纸
    )
    override val navContainer = DoodlePaper
    override val navItemUnselected = Color(0x992C2C2C)
    override val shadowInk = DoodleTeal                    // 涂鸦卡片走硬偏移阴影（见 ProCard）
    override val shadowAmbientAlpha = 1f
    override val shadowSpotAlpha = 1f
    override val ink = DoodleInk
    override val peach = DoodleRed
    override val teal = DoodleTeal
    override val rose = DoodleRed
    override val sand = DoodleYellow

    override val cardShape = RoundedCornerShape(6.dp)
    override val tileShape = RoundedCornerShape(4.dp)
    override val badgeShape = RoundedCornerShape(4.dp)
    override val buttonShape = RoundedCornerShape(6.dp)
    override val pillShape = RoundedCornerShape(4.dp)
    override val dashedBorders = true
    override val bannerBrush = SolidColor(primary)         // 禁渐变：扁平墨色横幅
}

// ── 单色极简 / Monochrome（第四版）──────────────────────────────
// 提示词：风格提示词/单色极简.md。规则：只用灰阶九色（#111~#fff，禁一切色相）；
// 直角~2dp 小圆角（禁 rounded-full）；禁渐变、禁重阴影（shadow-sm 上限）；
// 层次靠字重对比（标题 bold tracking-tight / 正文 light）与大量负空间。

/** 灰阶九色（提示词给定的全部合法色；仅单色场景与调色板使用） */
val MonoInk = Color(0xFF111111)
val MonoGray900 = Color(0xFF333333)
val MonoGray700 = Color(0xFF666666)
val MonoGray500 = Color(0xFF999999)
val MonoGray300 = Color(0xFFCCCCCC)
val MonoGray200 = Color(0xFFE5E5E5)
val MonoGray100 = Color(0xFFF5F5F5)
val MonoPaper = Color(0xFFFAFAFA)

object MonoPalette : AppPalette {
    override val mode = ThemeMode.MONO
    override val label = "单色极简"
    override val tagline = "黑白灰 · 字重层次"
    override val previewDots = listOf(MonoPaper, MonoInk, MonoGray300)

    override val bgPrimary = MonoPaper
    override val primary = MonoInk                        // 主按钮 = 墨黑底 + 纸色字
    override val primaryDark = MonoGray900
    override val primaryLight = MonoGray700
    override val accentGreen = MonoGray700                // 语义槽位全部退化为灰阶
    override val cardGreenLight = MonoGray200             // 选中态 = 浅灰底（不靠色相区分）
    override val cardOrange = MonoGray100
    override val cardCream = MonoGray100
    override val mutedSurface = MonoGray100
    override val gold = MonoGray700
    override val textPrimary = MonoInk
    override val textSecondary = MonoGray700
    override val textPlaceholder = MonoGray500
    override val dividerColor = MonoGray200               // 极细边框分隔 #e5e5e5
    override val borderColor = MonoGray200
    override val surfaceCard = Color.White
    override val dialogContainer = Color.White
    // 状态色也必须无色相：用灰阶深浅 + 文案表达语义（提示词的硬约束）
    override val errorRed = MonoInk
    override val warningAmber = MonoGray700
    override val successGreen = MonoGray900
    override val disabledGray = MonoGray300
    override val taskCardPalette = listOf(
        MonoGray100 to MonoInk,           // 浅灰
        MonoGray200 to MonoInk,           // 灰
        Color.White to MonoInk,           // 纸白（靠描边区分底色）
        MonoGray300 to MonoInk,           // 中浅灰
        MonoGray500 to MonoInk,           // 中灰（对比度 ≈6:1）
        MonoGray700 to MonoPaper,         // 深灰（浅字 ≈5.4:1）
        MonoGray900 to MonoPaper,         // 近黑
        MonoInk to MonoPaper,             // 墨卡
        MonoGray200 to MonoGray900,       // 灰（深字变化）
        MonoGray100 to MonoGray700,
        MonoGray300 to MonoGray900,
        Color.White to MonoGray700,
        MonoGray500 to Color.White,       // 中灰（白字 ≈3.9:1，大号图标语义可接受）
        MonoGray700 to Color.White,
        MonoGray900 to MonoGray200,
        MonoInk to MonoGray300,
    )
    override val navContainer = MonoPaper
    override val navItemUnselected = MonoGray500
    override val shadowInk = MonoInk                      // 阴影近乎不可见（shadow-sm 上限）
    override val shadowAmbientAlpha = 0.08f
    override val shadowSpotAlpha = 0.06f
    override val ink = MonoInk
    override val peach = MonoGray700
    override val teal = MonoGray500
    override val rose = MonoGray900
    override val sand = MonoGray300

    override val cardShape = RoundedCornerShape(2.dp)     // rounded-sm
    override val tileShape = RoundedCornerShape(2.dp)
    override val badgeShape = RoundedCornerShape(2.dp)
    override val buttonShape = RoundedCornerShape(2.dp)
    override val pillShape = RoundedCornerShape(2.dp)     // 禁 rounded-full：胶囊退化为小圆角
    override val dashedBorders = false
    override val bannerBrush = SolidColor(primary)        // 禁渐变：扁平墨色横幅
}

// ── 水墨画风 / Ink Wash（第五版）────────────────────────────────
// 提示词：风格提示词/水墨画风.md。规则：宣纸暖底 + 墨色文字 + 苔绿/茶棕/沙三点缀；
// 禁饱和色、禁重阴影、禁渐变（横幅走纯色）、禁粗边框（墨 20% 细线）；
// 层次靠墨色浓淡（墨分五色：焦浓重淡清）与大量留白；形状 2dp 微圆角。

/** 水墨场景色（墨分五色取三档 + 一点胭脂；仅水墨场景层与计时环共用） */
val InkNong = Color(0xFF2C2C2C)      // 浓墨（主文字/主笔触）
val InkQing = Color(0xFF8E8C84)      // 清墨（淡笔/底稿）
val InkYanzhi = Color(0xFFA97070)    // 胭脂（紧迫提示，印章红降饱和）
val PaperXuan = Color(0xFFF8F5F0)    // 宣纸
val PaperXuanSoft = Color(0xFFF3EFE8) // 辅宣纸
val MossGreen = Color(0xFF6B7B6E)    // 苔绿
val TeaBrown = Color(0xFFA89279)     // 茶棕
val SandInk = Color(0xFFC4B9A8)      // 沙色

object InkwashPalette : AppPalette {
    override val mode = ThemeMode.INKWASH
    override val label = "水墨画风"
    override val tagline = "宣纸 · 墨分五色"
    override val previewDots = listOf(PaperXuan, InkNong, MossGreen)

    override val bgPrimary = PaperXuan
    override val primary = InkNong                        // 主按钮 = 焦墨底 + 宣纸字
    override val primaryDark = Color(0xFF1E1E1C)
    override val primaryLight = Color(0xFFA9A79D)         // 清墨（禁饱和高光色）
    override val accentGreen = MossGreen                  // 语义绿 = 苔绿（天然匹配）
    override val cardGreenLight = Color(0xFFE9ECE6)       // 苔绿染纸（选中态）
    override val cardOrange = Color(0xFFEFE8DD)           // 茶棕染纸
    override val cardCream = PaperXuanSoft
    override val mutedSurface = PaperXuanSoft
    override val gold = TeaBrown
    override val textPrimary = InkNong
    override val textSecondary = Color(0xA62C2C2C)        // 墨 65%
    override val textPlaceholder = Color(0x732C2C2C)      // 墨 45%
    override val dividerColor = Color(0x332C2C2C)         // 墨 20%：风格规范 border-[#2c2c2c]/20
    override val borderColor = Color(0x332C2C2C)
    override val surfaceCard = Color(0xFFFBF9F3)          // 略提亮的纸面
    override val dialogContainer = Color(0xFFFBF9F3)
    override val errorRed = InkYanzhi                     // 胭脂（禁饱和红）
    override val warningAmber = TeaBrown
    override val successGreen = MossGreen
    override val disabledGray = SandInk
    override val taskCardPalette = listOf(
        Color(0xFFECE8DE) to InkNong,     // 淡茶纸
        Color(0xFFE9ECE6) to InkNong,     // 苔绿染
        Color(0xFFEFE8DD) to InkNong,     // 茶棕染
        Color(0xFFE2DCCF) to InkNong,     // 沙色
        PaperXuanSoft to InkNong,         // 辅宣纸
        Color(0xFFE4E4DC) to InkNong,     // 淡墨
        Color(0xFFD8D5C8) to InkNong,     // 陈纸
        Color(0xFFDFE5E0) to InkNong,     // 青灰
        Color(0xFFC4B9A8) to InkNong,     // 沙色正
        InkNong to PaperXuan,             // 焦墨（纸色字）
        Color(0xFF4A554C) to PaperXuan,   // 深苔
        Color(0xFF6E5F50) to PaperXuan,   // 深茶
        Color(0xFF55524A) to PaperXuan,   // 浓墨灰
        Color(0xFF5C6857) to PaperXuan,   // 苔浓
        Color(0xFF3E4A44) to PaperXuan,   // 墨绿
        Color(0xFF33332F) to PaperXuan,   // 重墨
    )
    override val navContainer = Color(0xFFFBF9F3)
    override val navItemUnselected = Color(0x732C2C2C)
    override val shadowInk = InkNong
    override val shadowAmbientAlpha = 0.10f               // 禁重阴影：淡到几乎不可见
    override val shadowSpotAlpha = 0.08f
    override val ink = InkNong
    override val peach = TeaBrown
    override val teal = MossGreen
    override val rose = InkYanzhi
    override val sand = SandInk

    override val cardShape = RoundedCornerShape(2.dp)     // rounded-none 的 2dp 折衷
    override val tileShape = RoundedCornerShape(2.dp)
    override val badgeShape = RoundedCornerShape(2.dp)
    override val buttonShape = RoundedCornerShape(2.dp)
    override val pillShape = RoundedCornerShape(2.dp)     // 禁 rounded-full：胶囊退化为微圆角
    override val dashedBorders = false
    override val bannerBrush = SolidColor(primary)        // 禁渐变：扁平焦墨横幅
}

/** 全部主题（选择界面按此顺序展示） */
val AllPalettes: List<AppPalette> =
    listOf(WatercolorPalette, NocturnePalette, DoodlePalette, MonoPalette, InkwashPalette)

/** 当前生效的调色板（快照状态：切换后所有读取它的组合范围自动重组） */
var ActivePalette: AppPalette by mutableStateOf(NocturnePalette)

fun paletteFor(mode: ThemeMode): AppPalette =
    when (mode) {
        ThemeMode.WATERCOLOR -> WatercolorPalette
        ThemeMode.NOCTURNE -> NocturnePalette
        ThemeMode.DOODLE -> DoodlePalette
        ThemeMode.MONO -> MonoPalette
        ThemeMode.INKWASH -> InkwashPalette
    }

// ── 全站 token（读取 ActivePalette，调用点无需感知主题切换）──────
val BgPrimary: Color get() = ActivePalette.bgPrimary
val Primary: Color get() = ActivePalette.primary
val PrimaryDark: Color get() = ActivePalette.primaryDark
val PrimaryLight: Color get() = ActivePalette.primaryLight
val AccentGreen: Color get() = ActivePalette.accentGreen
val CardGreenLight: Color get() = ActivePalette.cardGreenLight
val CardOrange: Color get() = ActivePalette.cardOrange
val CardCream: Color get() = ActivePalette.cardCream
val MutedSurface: Color get() = ActivePalette.mutedSurface
val Gold: Color get() = ActivePalette.gold
val TextPrimary: Color get() = ActivePalette.textPrimary
val TextSecondary: Color get() = ActivePalette.textSecondary
val TextPlaceholder: Color get() = ActivePalette.textPlaceholder
val DividerColor: Color get() = ActivePalette.dividerColor
val BorderColor: Color get() = ActivePalette.borderColor
val SurfaceCard: Color get() = ActivePalette.surfaceCard
val ErrorRed: Color get() = ActivePalette.errorRed
val WarningAmber: Color get() = ActivePalette.warningAmber
val SuccessGreen: Color get() = ActivePalette.successGreen
val DisabledGray: Color get() = ActivePalette.disabledGray
val TaskCardPalette: List<Pair<Color, Color>> get() = ActivePalette.taskCardPalette
val NavContainer: Color get() = ActivePalette.navContainer
val NavItemUnselected: Color get() = ActivePalette.navItemUnselected
/** 弹窗/弹层容器色（比卡片更实一层，保证可读性）；底部弹层与渐隐遮罩都用它 */
val DialogContainer: Color get() = ActivePalette.dialogContainer

/** 旧品牌色名 → 当前调色板（水彩四色 / 夜航月光 / 涂鸦标记笔） */
val WatercolorInk: Color get() = ActivePalette.ink
val WatercolorPeach: Color get() = ActivePalette.peach
val WatercolorTeal: Color get() = ActivePalette.teal
val WatercolorRose: Color get() = ActivePalette.rose
val WatercolorSand: Color get() = ActivePalette.sand

/**
 * 历史别名（旧代码引用点保留）。
 * PaperElevated 的全部现存引用都是弹窗/弹层容器与滚轮渐隐遮罩，故指向
 * dialogContainer（不透明、比卡片实一层）；此前错指 navContainer——
 * 夜航主题下 navContainer 是 85% 透明的磨砂条底色，弹层会透出下层内容。
 */
val PaperBase: Color get() = ActivePalette.bgPrimary
val PaperSoft: Color get() = ActivePalette.cardCream
val PaperElevated: Color get() = ActivePalette.dialogContainer
