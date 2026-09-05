package com.shihuaidexianyu.money.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shihuaidexianyu.money.domain.model.AmountColorMode

// ===== Semantic colors =====
internal const val LIGHT_INCOME_ARGB: Long = 0xFFA94442
internal const val LIGHT_EXPENSE_ARGB: Long = 0xFF2F6B4F
internal const val DARK_INCOME_ARGB: Long = 0xFFE57373
internal const val DARK_EXPENSE_ARGB: Long = 0xFF66BB6A
val CoralRed = Color(LIGHT_INCOME_ARGB)
val SageGreen = Color(LIGHT_EXPENSE_ARGB)
private val LightLedgerNeutral = Color(0xFF606060)
private val DarkLedgerNeutral = Color(0xFFBDBDBD)

// ===== Backgrounds =====
// Neutral ledger canvas keeps semantic colors reserved for financial meaning.
val BackgroundSoft = Color(0xFFFAFAFA)
val SurfaceVariantNeutral = Color(0xFFE5E5E5)
val InputSurfaceNeutral = Color(0xFFF0F0F0)

// ===== Text =====
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF626262)
val TextDisabled = Color(0xFFDADADA)

// ===== Borders & dividers =====
val BorderNeutral = Color(0xFFE0E0E0)
val BorderFocusedNeutral = Color(0xFF858585)

// ===== Monochrome brand colors =====
val BrandPrimary = Color(0xFF202020)
val BrandPrimaryContainer = Color(0xFFE8E8E8)
val BrandPrimaryDark = Color(0xFFE8E8E8)
val BrandOnPrimaryContainer = Color(0xFF202020)
val BrandSecondary = Color(0xFF5C5C5C)
val BrandSecondaryContainer = Color(0xFFEDEDED)
val BrandOnSecondaryContainer = Color(0xFF292929)
val BrandTertiary = Color(0xFF606060)
val BrandTertiaryContainer = Color(0xFFEAEAEA)
val BrandOnTertiaryContainer = Color(0xFF252525)

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
    current = LightLedgerNeutral,
    transfer = LightLedgerNeutral,
    reminder = LightLedgerNeutral,
)

private val DarkMoneyColors = MoneyColors(
    income = Color(DARK_INCOME_ARGB),
    expense = Color(DARK_EXPENSE_ARGB),
    current = DarkLedgerNeutral,
    transfer = DarkLedgerNeutral,
    reminder = DarkLedgerNeutral,
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
