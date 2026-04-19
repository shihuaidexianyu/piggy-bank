package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.shihuaidexianyu.money.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Amber600,
    onPrimary = SurfaceWhite,
    primaryContainer = Amber100,
    onPrimaryContainer = CharcoalWarm,
    secondary = CoralRed,
    onSecondary = SurfaceWhite,
    tertiary = SageGreen,
    onTertiary = SurfaceWhite,
    background = BackgroundCream,
    onBackground = CharcoalWarm,
    surface = SurfaceWhite,
    onSurface = CharcoalWarm,
    surfaceVariant = InputBg,
    onSurfaceVariant = WarmGray,
    outline = WarmBorderFocused,
    outlineVariant = WarmBorder,
    error = CoralRed,
    onError = SurfaceWhite,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = CoralRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5B84C),
    onPrimary = Night950,
    primaryContainer = Color(0xFF4A3A1A),
    onPrimaryContainer = Color(0xFFFFF3D6),
    secondary = Color(0xFFE57373),
    onSecondary = Night950,
    secondaryContainer = Color(0xFF4A2A2A),
    onSecondaryContainer = Color(0xFFFDE8E8),
    tertiary = Color(0xFF66BB6A),
    onTertiary = Night950,
    tertiaryContainer = Color(0xFF1A3A2A),
    onTertiaryContainer = Color(0xFFE8F5E9),
    background = Night950,
    onBackground = Color(0xFFF5F0E8),
    surface = Night800,
    onSurface = Color(0xFFF5F0E8),
    surfaceVariant = Night700,
    onSurfaceVariant = Color(0xFFB5AFA8),
    outline = Color(0xFF4A4844),
    outlineVariant = Color(0xFF3A3834),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MoneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val moneyColors = if (darkTheme) DarkMoneyColorsInstance else LightMoneyColorsInstance

    CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneyTypography,
            content = content,
        )
    }
}
