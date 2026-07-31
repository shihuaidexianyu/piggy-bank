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
// Neutral surfaces: pure white in light, neutral grays for variants/inputs. No warm tints.
val BackgroundPure = Color(0xFFFFFFFF)
val SurfacePure = Color(0xFFFFFFFF)
val SurfaceVariantNeutral = Color(0xFFF5F5F5)
val InputSurfaceNeutral = Color(0xFFF5F5F5)

// ===== Text =====
val TextPrimary = Color(0xFF202124)
val TextSecondary = Color(0xFF5F6368)
val TextDisabled = Color(0xFFDADCE0)

// ===== Borders & dividers =====
val BorderNeutral = Color(0xFFE0E0E0)
val BorderFocusedNeutral = Color(0xFFBDBDBD)

// ===== Dark mode =====
val Night950 = Color(0xFF141210)
val Night800 = Color(0xFF201C18)
val Night700 = Color(0xFF2B2621)
val Night600 = Color(0xFF3B342D)

// ===== Brand colors (primary teal) =====
val BrandTealPrimary = Color(0xFF256F70)
val BrandTealPrimaryContainer = Color(0xFFDCEEEF)
val BrandTealPrimaryDark = Color(0xFF72CCCB)

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
    current = Color(0xFFE5B84C),
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
