package com.shihuaidexianyu.money.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shihuaidexianyu.money.domain.model.AmountColorMode

// ===== Primary palette =====
val Amber600 = Color(0xFFC4943A)
val Amber400 = Color(0xFFD4A85A)
val Amber100 = Color(0xFFFFF3D6)

// ===== Semantic colors =====
internal const val LIGHT_INCOME_ARGB: Long = 0xFFA94442
internal const val LIGHT_EXPENSE_ARGB: Long = 0xFF2F6B4F
internal const val DARK_INCOME_ARGB: Long = 0xFFE57373
internal const val DARK_EXPENSE_ARGB: Long = 0xFF66BB6A
val CoralRed = Color(LIGHT_INCOME_ARGB)
val SageGreen = Color(LIGHT_EXPENSE_ARGB)
val TransferBlue = Color(0xFF5B8DB8)
val ReminderPurple = Color(0xFF9B7CB6)

// ===== Backgrounds =====
// 青玉 canvas: a quiet jade-tinted gray separates the page from pure-white cards.
val BackgroundSoft = Color(0xFFF3F6F5)
val SurfaceVariantNeutral = Color(0xFFDAE3E1)
val InputSurfaceNeutral = Color(0xFFE9EFEE)

// ===== Text =====
val TextPrimary = Color(0xFF14201F)
val TextSecondary = Color(0xFF5B6B69)
val TextDisabled = Color(0xFFDADCE0)

// ===== Borders & dividers =====
val BorderNeutral = Color(0xFFD8E2E0)
val BorderFocusedNeutral = Color(0xFF6F7F7D)

// ===== Brand colors (青玉 deep jade) =====
val BrandTealPrimary = Color(0xFF0B5F62)
val BrandTealPrimaryContainer = Color(0xFFC4E8E2)
val BrandTealPrimaryDark = Color(0xFF7FD3CC)
val BrandTealOnPrimaryContainer = Color(0xFF05383A)
val BrandTealSecondary = Color(0xFF4B6363)
val BrandTealSecondaryContainer = Color(0xFFD3E9E6)
val BrandTealOnSecondaryContainer = Color(0xFF0F2B2A)
val BrandSlateTertiary = Color(0xFF4A607C)
val BrandSlateTertiaryContainer = Color(0xFFD3E4FF)
val BrandSlateOnTertiaryContainer = Color(0xFF031C35)

data class MoneyColors(
    val income: Color,
    val expense: Color,
    val current: Color,
    val transfer: Color,
    val reminder: Color,
)

private val LightMoneyColors = MoneyColors(
    income = CoralRed,
    expense = SageGreen,
    current = Amber600,
    transfer = TransferBlue,
    reminder = ReminderPurple,
)

private val DarkMoneyColors = MoneyColors(
    income = Color(DARK_INCOME_ARGB),
    expense = Color(DARK_EXPENSE_ARGB),
    current = Amber400,
    transfer = Color(0xFF90CAF9),
    reminder = Color(0xFFCE93D8),
)

private val LightInvertedMoneyColors = LightMoneyColors.copy(
    income = SageGreen,
    expense = CoralRed,
)

private val DarkInvertedMoneyColors = DarkMoneyColors.copy(
    income = Color(DARK_EXPENSE_ARGB),
    expense = Color(DARK_INCOME_ARGB),
)

fun moneyColorsFor(
    darkTheme: Boolean,
    amountColorMode: AmountColorMode,
): MoneyColors {
    return when (amountColorMode) {
        AmountColorMode.RED_INCOME_GREEN_EXPENSE -> if (darkTheme) DarkMoneyColors else LightMoneyColors
        AmountColorMode.GREEN_INCOME_RED_EXPENSE -> if (darkTheme) DarkInvertedMoneyColors else LightInvertedMoneyColors
    }
}

val LocalMoneyColors = compositionLocalOf { LightMoneyColors }
