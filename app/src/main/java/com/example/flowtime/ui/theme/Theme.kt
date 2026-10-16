package com.example.flowtime.ui.theme

import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 多主题入口：按 themeMode 构建对应的 Material 配色/字体/形状，并把全局调色板
 * （ActivePalette，全站 token 的数据源）同步到该主题。
 * 在组合期同步而非 SideEffect：同一帧内"先写后读"，切换瞬间不会出现
 * 新旧配色混排的一帧。
 */
@Composable
fun FlowTimeTheme(
    themeMode: ThemeMode = ThemeMode.NOCTURNE,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeMode)
    ActivePalette = palette
    val colorScheme = when (palette.mode) {
        ThemeMode.NOCTURNE -> nocturneScheme(palette)
        ThemeMode.DOODLE -> doodleScheme(palette)
        ThemeMode.WATERCOLOR -> watercolorScheme(palette)
        ThemeMode.MONO -> monoScheme(palette)
        ThemeMode.GRADIENT -> gradientScheme(palette)
        ThemeMode.PARTICLES -> particleScheme(palette)
    }
    SceneSystemBarsEffect(palette)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typographyFor(palette.mode),
        shapes = shapesFor(palette.mode),
        content = content
    )
}

/** 水彩画风（浅色纸底）*/
private fun watercolorScheme(p: AppPalette) = lightColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = p.textPrimary,
    secondary = p.accentGreen,
    onSecondary = p.textPrimary,
    tertiary = p.gold,
    onTertiary = p.textPrimary,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = p.surfaceCard,
    onSurface = p.textPrimary,
    surfaceVariant = p.cardCream,
    onSurfaceVariant = p.textSecondary,
    outline = p.dividerColor,
    outlineVariant = p.borderColor,
    error = p.errorRed,
)

/** 玻璃拟态（夜航深色；基于 darkColorScheme，容器档取深色）*/
private fun nocturneScheme(p: AppPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = OnChampagne,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = ChampagneBright,
    secondary = p.accentGreen,
    onSecondary = NightDeep,
    secondaryContainer = MoonSteel,
    onSecondaryContainer = Color(0xFFDCE7F5),
    tertiary = p.successGreen,
    onTertiary = NightDeep,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = SteelLift,
    onSurface = p.textPrimary,
    surfaceVariant = Color(0xFF1D2B44),
    onSurfaceVariant = p.textSecondary,
    surfaceContainerLow = Color(0xFF101A2C),
    surfaceContainer = SteelLift,
    surfaceContainerHigh = Color(0xFF1B2A44),
    surfaceContainerHighest = Color(0xFF20304C),
    outline = p.dividerColor,
    outlineVariant = Color(0x14FFFFFF),
    error = p.errorRed,
)

/** 手绘涂鸦（浅色纸底；墨黑主按钮 + 标记笔三色强调）*/
private fun doodleScheme(p: AppPalette) = lightColorScheme(
    primary = p.primary,
    onPrimary = DoodlePaper,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = p.textPrimary,
    secondary = p.accentGreen,
    onSecondary = DoodlePaper,
    tertiary = p.warningAmber,
    onTertiary = p.textPrimary,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = p.surfaceCard,
    onSurface = p.textPrimary,
    surfaceVariant = p.cardCream,
    onSurfaceVariant = p.textSecondary,
    outline = p.dividerColor,
    outlineVariant = p.dividerColor,
    error = p.errorRed,
)

/** 单色极简（#fafafa 纸底；墨黑主按钮 + 全灰阶语义色，零色相）*/
private fun monoScheme(p: AppPalette) = lightColorScheme(
    primary = p.primary,
    onPrimary = MonoPaper,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = p.textPrimary,
    secondary = p.accentGreen,
    onSecondary = MonoPaper,
    tertiary = p.gold,
    onTertiary = MonoPaper,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = p.surfaceCard,
    onSurface = p.textPrimary,
    surfaceVariant = p.cardCream,
    onSurfaceVariant = p.textSecondary,
    outline = p.dividerColor,
    outlineVariant = p.borderColor,
    error = p.errorRed,
)

/** 现代渐变（深紫底，基于 darkColorScheme；白字 + 紫/品红/青渐变强调）*/
private fun gradientScheme(p: AppPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = p.textPrimary,
    secondary = p.accentGreen,
    onSecondary = Color(0xFF04212B),
    secondaryContainer = Color(0x3306B6D4),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = p.gold,
    onTertiary = GradNightDeep,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = p.surfaceCard,
    onSurface = p.textPrimary,
    surfaceVariant = p.cardCream,
    onSurfaceVariant = p.textSecondary,
    surfaceContainerLow = Color(0xFF181437),
    surfaceContainer = Color(0xFF1E1B4B),
    surfaceContainerHigh = Color(0xFF2A2159),
    surfaceContainerHighest = Color(0xFF342A6B),
    outline = p.dividerColor,
    outlineVariant = p.borderColor,
    error = p.errorRed,
)

/** 粒子系统（深蓝黑底；基于 darkColorScheme，容器档取蓝黑阶）*/
private fun particleScheme(p: AppPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.cardGreenLight,
    onPrimaryContainer = p.textPrimary,
    secondary = p.accentGreen,
    onSecondary = Color(0xFF04211F),
    secondaryContainer = Color(0x3314B8A6),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = p.gold,
    onTertiary = Color.White,
    background = p.bgPrimary,
    onBackground = p.textPrimary,
    surface = p.surfaceCard,
    onSurface = p.textPrimary,
    surfaceVariant = p.cardCream,
    onSurfaceVariant = p.textSecondary,
    surfaceContainerLow = Color(0xFF0D1320),
    surfaceContainer = Color(0xFF0F1419),
    surfaceContainerHigh = Color(0xFF161D2B),
    surfaceContainerHighest = Color(0xFF1E2738),
    outline = p.dividerColor,
    outlineVariant = p.borderColor,
    error = p.errorRed,
)

/**
 * 字体按主题走：
 * 水彩 = 标题衬线轻字重宽字距 + 正文无衬线轻字重（风格提示词 font-serif font-light tracking-wide）；
 * 涂鸦 = 无衬线 + 加粗（手绘笔记的随意感）；夜航 = M3 默认。
 */
private fun typographyFor(mode: ThemeMode): Typography = when (mode) {
    ThemeMode.WATERCOLOR -> Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 1.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 1.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
            fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.8.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
            fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.6.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.4.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 16.sp, lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 14.sp, lineHeight = 22.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 12.sp, lineHeight = 18.sp,
        ),
    )
    ThemeMode.DOODLE -> Typography(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.2.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
    )
    // 单色极简：标题 bold + 紧字距（tracking-tight），正文 light，
    // 小字/标签加宽字距（tracking-wider）——层次全靠字重与字距，不靠颜色
    ThemeMode.MONO -> Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.25).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.25).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
            fontSize = 16.sp, lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 16.sp, lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 14.sp, lineHeight = 22.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
            fontSize = 12.sp, lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp,
        ),
    )
    // 现代渐变：全无衬线；标题 Bold（规范 TYPOGRAPHY「Headings: font-bold」），
    // 正文 Normal、小标签 Medium（font-medium~semibold）—— 现代科技感，不用衬线。
    ThemeMode.GRADIENT -> Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.25).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 22.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 18.sp,
        ),
    )
    // 粒子系统：全站无衬线（提示词 font-sans font-semibold 标题 / text-sm 正文），
    // 科技感靠字重与字距，不靠衬线
    ThemeMode.PARTICLES -> Typography(
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, lineHeight = 32.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, lineHeight = 28.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
            fontSize = 16.sp, lineHeight = 24.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 22.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 18.sp,
        ),
    )
    else -> Typography
}

/**
 * M3 组件圆角随主题整体缩放：
 * 水彩放大（输入框 extraSmall=16dp ≈ rounded-2xl）；涂鸦收小；
 * 现代渐变放大到 rounded-2xl / rounded-3xl 区间（规范禁直角 rounded-none）。
 */
private fun shapesFor(mode: ThemeMode): Shapes = when (mode) {
    ThemeMode.WATERCOLOR -> Shapes(
        extraSmall = RoundedCornerShape(16.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    ThemeMode.DOODLE -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
    ThemeMode.GRADIENT -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),      // rounded-3xl
        extraLarge = RoundedCornerShape(28.dp),
    )
    // 粒子系统：rounded-xl 卡片 / rounded-lg 按钮（提示词 Token 字典）
    ThemeMode.PARTICLES -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),       // rounded-lg
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp),      // rounded-xl
        extraLarge = RoundedCornerShape(16.dp),
    )
    else -> Shapes()
}

/**
 * 场景随主题走：状态栏/导航栏图标颜色（深色场景浅色图标，浅色场景深色图标），
 * 以及窗口底色（换成当前主题的纸底/夜底，冷启动不闪错色）。
 */
@Composable
private fun SceneSystemBarsEffect(palette: AppPalette) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? ComponentActivity ?: return@SideEffect
        val window = activity.window
        // 深色主题（夜航 / 现代渐变 / 粒子系统）→ 浅色系统图标；窗口底色取各自的主题底，冷启动不闪错色
        if (palette.mode == ThemeMode.NOCTURNE || palette.mode == ThemeMode.GRADIENT ||
            palette.mode == ThemeMode.PARTICLES
        ) {
            window.setBackgroundDrawable(ColorDrawable(palette.bgPrimary.toArgb()))
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
                navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            )
        } else {
            window.setBackgroundDrawable(ColorDrawable(palette.bgPrimary.toArgb()))
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    Color.Transparent.toArgb(),
                    Color.Transparent.toArgb(),
                ),
                navigationBarStyle = SystemBarStyle.light(
                    Color.Transparent.toArgb(),
                    Color.Transparent.toArgb(),
                ),
            )
        }
    }
}
