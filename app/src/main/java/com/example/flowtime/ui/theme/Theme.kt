package com.example.flowtime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = CardGreenLight,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGreen,
    onSecondary = TextPrimary,
    tertiary = Gold,
    onTertiary = TextPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = CardCream,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    outlineVariant = BorderColor,
    error = ErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNight,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = AccentGreen,
    onSecondary = TextPrimary,
    tertiary = Gold,
    onTertiary = TextPrimary,
    background = BgPrimaryNight,
    onBackground = TextPrimaryNight,
    surface = SurfaceNight,
    onSurface = TextPrimaryNight,
    surfaceVariant = SurfaceNight,
    onSurfaceVariant = TextSecondaryNight,
    outline = DividerNight,
    error = ErrorRed,
)

@Composable
fun FlowTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
