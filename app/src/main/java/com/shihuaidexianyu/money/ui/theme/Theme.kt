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
    // The page canvas doubles as `surface` so app bars, sheets, and the scaffold share one
    // jade-tinted ground; white is reserved for cards via surfaceContainerLowest.
    surface = BackgroundSoft,
    surfaceVariant = SurfaceVariantNeutral,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainer = Color(0xFFEDF2F1),
    surfaceContainerHigh = Color(0xFFE4EBE9),
    surfaceContainerHighest = Color(0xFFDDE6E4),
    surfaceDim = Color(0xFFD5E0DE),
    surfaceBright = Color(0xFFFFFFFF),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceTint = BrandTealPrimary,
    inverseSurface = Color(0xFF17211F),
    inverseOnSurface = Color(0xFFE4EFED),
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
    onPrimary = Color(0xFF063A3C),
    primaryContainer = Color(0xFF11484A),
    onPrimaryContainer = Color(0xFFB9ECE6),
    inversePrimary = BrandTealPrimary,
    secondary = Color(0xFFB1CCCB),
    onSecondary = Color(0xFF1C3535),
    secondaryContainer = Color(0xFF22403D),
    onSecondaryContainer = Color(0xFFC4E8E2),
    tertiary = Color(0xFFB5C9E8),
    onTertiary = Color(0xFF1F3048),
    tertiaryContainer = Color(0xFF36465F),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF0F1615),
    surface = Color(0xFF0F1615),
    surfaceVariant = Color(0xFF33403E),
    // Deep jade-gray ladder: the page sits at #0F1615 and cards/sheets step up from #18211F,
    // so elevation stays readable without flattening into one black plane.
    surfaceContainerLowest = Color(0xFF18211F),
    surfaceContainerLow = Color(0xFF1C2624),
    surfaceContainer = Color(0xFF212B29),
    surfaceContainerHigh = Color(0xFF27332F),
    surfaceContainerHighest = Color(0xFF2E3A37),
    surfaceDim = Color(0xFF0F1615),
    surfaceBright = Color(0xFF36423F),
    onBackground = Color(0xFFE4EFED),
    onSurface = Color(0xFFE4EFED),
    onSurfaceVariant = Color(0xFF9DB4B1),
    surfaceTint = BrandTealPrimaryDark,
    inverseSurface = Color(0xFFE4EFED),
    inverseOnSurface = Color(0xFF17211F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5E6F6C),
    outlineVariant = Color(0xFF33403E),
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
            shapes = MoneyShapes,
            content = content,
        )
    }
}
