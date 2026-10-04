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

/**
 * 涂鸦主题字体：无衬线 + 加粗（手绘笔记的随意感）；
 * 水彩/夜航沿用衬线轻字重的旧排版。
 */
private fun typographyFor(mode: ThemeMode): Typography = when (mode) {
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
    else -> Typography
}

/** 涂鸦主题把 M3 组件圆角整体收小（弹窗/弹层 rounded-sm 档），其余用 M3 默认 */
private fun shapesFor(mode: ThemeMode): Shapes = when (mode) {
    ThemeMode.DOODLE -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
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
        if (palette.mode == ThemeMode.NOCTURNE) {
            window.setBackgroundDrawable(ColorDrawable(NightBase.toArgb()))
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
