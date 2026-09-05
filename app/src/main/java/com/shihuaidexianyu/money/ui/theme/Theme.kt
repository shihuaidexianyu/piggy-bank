package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol

val LocalDarkTheme = staticCompositionLocalOf { false }

private val BrandLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    inversePrimary = BrandPrimaryDark,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer,
    background = BackgroundSoft,
    // Neutral surfaces keep navigation and form controls separate from financial colors.
    surface = BackgroundSoft,
    surfaceVariant = SurfaceVariantNeutral,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE9E9E9),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    surfaceDim = Color(0xFFD6D6D6),
    surfaceBright = Color(0xFFFFFFFF),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceTint = BrandPrimary,
    inverseSurface = Color(0xFF252525),
    inverseOnSurface = Color(0xFFF5F5F5),
    error = CoralRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outlineVariant = BorderNeutral,
    outline = BorderFocusedNeutral,
    scrim = Color.Black,
)

private val BrandDarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF181818),
    primaryContainer = Color(0xFF303030),
    onPrimaryContainer = Color(0xFFFAFAFA),
    inversePrimary = BrandPrimary,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF242424),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFFC6C6C6),
    onTertiary = Color(0xFF262626),
    tertiaryContainer = Color(0xFF353535),
    onTertiaryContainer = Color(0xFFEAEAEA),
    background = Color(0xFF141414),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF393939),
    // A neutral gray ladder keeps cards and sheets distinct from the charcoal page.
    surfaceContainerLowest = Color(0xFF1C1C1C),
    surfaceContainerLow = Color(0xFF222222),
    surfaceContainer = Color(0xFF282828),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF393939),
    surfaceDim = Color(0xFF141414),
    surfaceBright = Color(0xFF3D3D3D),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceTint = BrandPrimaryDark,
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF252525),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF909090),
    outlineVariant = Color(0xFF3D3D3D),
    scrim = Color.Black,
)

@Composable
fun MoneyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    amountColorMode: AmountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE,
    currencySymbol: String = "¥",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) BrandDarkColorScheme else BrandLightColorScheme
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
            shapes = MoneyShapes,
            content = content,
        )
    }
}
