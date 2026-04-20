package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.shihuaidexianyu.money.domain.model.AmountColorMode
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
    surface = Color(0xFFFFFCF8),
    onSurface = CharcoalWarm,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = WarmGray,
    outline = WarmBorderFocused,
    outlineVariant = WarmBorder,
    error = CoralRed,
    onError = SurfaceWhite,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = CoralRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0B55D),
    onPrimary = Night950,
    primaryContainer = Color(0xFF5A4720),
    onPrimaryContainer = Color(0xFFFFF3D6),
    secondary = Color(0xFFED8A81),
    onSecondary = Night950,
    secondaryContainer = Color(0xFF53302F),
    onSecondaryContainer = Color(0xFFFDE8E8),
    tertiary = Color(0xFF7AC286),
    onTertiary = Night950,
    tertiaryContainer = Color(0xFF223B2D),
    onTertiaryContainer = Color(0xFFE8F5E9),
    background = Night950,
    onBackground = Color(0xFFF4EEE7),
    surface = Night800,
    onSurface = Color(0xFFF4EEE7),
    surfaceVariant = Night700,
    onSurfaceVariant = Color(0xFFC9C0B6),
    outline = Color(0xFF61584F),
    outlineVariant = Color(0xFF494138),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun MoneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val moneyColors = moneyColorsFor(
        darkTheme = darkTheme,
        amountColorMode = amountColorMode,
    )

    CompositionLocalProvider(LocalMoneyColors provides moneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneyTypography,
            content = content,
        )
    }
}
