package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol

private val BrandLightColorScheme = lightColorScheme(
    primary = BrandTealPrimary,
    primaryContainer = BrandTealPrimaryContainer,
    background = BackgroundPure,
    surface = SurfacePure,
    surfaceVariant = SurfaceVariantNeutral,
    // Neutral surface containers: M3 defaults are purple-tinted, which shows through on
    // bottom sheets, dialogs, the nav bar, and elevated cards.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFEBEBEB),
    surfaceDim = Color(0xFFE0E0E0),
    surfaceBright = Color(0xFFFFFFFF),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outlineVariant = BorderNeutral,
    outline = BorderFocusedNeutral,
)

private val BrandDarkColorScheme = darkColorScheme(
    primary = BrandTealPrimaryDark,
    primaryContainer = Night700,
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
    outline = Color(0xFF4A4A4A),
    outlineVariant = Night600,
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
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneyTypography,
            content = content,
        )
    }
}
