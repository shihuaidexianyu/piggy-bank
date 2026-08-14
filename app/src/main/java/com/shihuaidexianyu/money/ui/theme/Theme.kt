package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol

val LocalDarkTheme = staticCompositionLocalOf { false }

private val BrandLightColorScheme = lightColorScheme(
    primary = BrandTealPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandTealPrimaryContainer,
    onPrimaryContainer = BrandTealOnPrimaryContainer,
    inversePrimary = BrandTealPrimaryDark,
    secondary = BrandTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrandTealSecondaryContainer,
    onSecondaryContainer = BrandTealOnSecondaryContainer,
    tertiary = BrandSlateTertiary,
    onTertiary = Color.White,
    tertiaryContainer = BrandSlateTertiaryContainer,
    onTertiaryContainer = BrandSlateOnTertiaryContainer,
    background = BackgroundSoft,
    surface = SurfacePure,
    surfaceVariant = SurfaceVariantNeutral,
    // Neutral surface containers: M3 defaults are purple-tinted, which shows through on
    // bottom sheets, dialogs, the nav bar, and elevated cards.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFB),
    surfaceContainer = Color(0xFFF1F4F4),
    surfaceContainerHigh = Color(0xFFE9EEEE),
    surfaceContainerHighest = Color(0xFFE1E7E7),
    surfaceDim = Color(0xFFDCE3E3),
    surfaceBright = Color(0xFFFFFFFF),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceTint = BrandTealPrimary,
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFF0F1F1),
    error = CoralRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outlineVariant = BorderNeutral,
    outline = BorderFocusedNeutral,
    scrim = Color.Black,
)

private val BrandDarkColorScheme = darkColorScheme(
    primary = BrandTealPrimaryDark,
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF0F5152),
    onPrimaryContainer = Color(0xFF9EF0EE),
    inversePrimary = BrandTealPrimary,
    secondary = Color(0xFFB1CCCB),
    onSecondary = Color(0xFF1C3535),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCDE8E7),
    tertiary = Color(0xFFB5C9E8),
    onTertiary = Color(0xFF1F3048),
    tertiaryContainer = Color(0xFF36465F),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Night700,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Night800,
    surfaceContainer = Night700,
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Night600,
    surfaceDim = Color.Black,
    surfaceBright = Night600,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = BrandTealPrimaryDark,
    inverseSurface = Color(0xFFE1E3E3),
    inverseOnSurface = Color(0xFF2E3131),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Night600,
    scrim = Color.Black,
)

@Composable
fun MoneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    useDynamicColor: Boolean = true,
    currencySymbol: String = "¥",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> BrandDarkColorScheme
        else -> BrandLightColorScheme
    }
    val moneyColors = moneyColorsFor(
        darkTheme = darkTheme,
        amountColorMode = amountColorMode,
    )

    CompositionLocalProvider(
        LocalMoneyColors provides moneyColors,
        LocalCurrencySymbol provides currencySymbol,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneyTypography,
            content = content,
        )
    }
}
