package com.shihuaidexianyu.money.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ===== Primary palette =====
val Amber600 = Color(0xFFC4943A)
val Amber400 = Color(0xFFD4A85A)
val Amber100 = Color(0xFFFFF3D6)

// ===== Semantic colors =====
val CoralRed = Color(0xFFC45C4A)
val SageGreen = Color(0xFF5A8A6E)
val TransferBlue = Color(0xFF5B8DB8)
val ReminderPurple = Color(0xFF9B7CB6)

// ===== Backgrounds =====
val BackgroundCream = Color(0xFFFAF8F5)
val SurfaceWarm = Color(0xFFF5F0E8)
val SurfaceWhite = Color(0xFFFFFFFF)
val InputBg = Color(0xFFF5F2ED)

// ===== Text =====
val CharcoalWarm = Color(0xFF2C2721)
val WarmGray = Color(0xFF6B6560)
val MutedWarm = Color(0xFFD5CFC8)

// ===== Borders & dividers =====
val WarmBorder = Color(0xFFF0EDE8)
val WarmBorderFocused = Color(0xFFE0DCD6)

// ===== Dark mode =====
val Night950 = Color(0xFF1A1816)
val Night800 = Color(0xFF242220)
val Night700 = Color(0xFF2E2C29)
val Night600 = Color(0xFF3A3834)

// ===== Legacy aliases for backward compatibility (to be removed later) =====
val Teal700 = Color(0xFF1E5D5E)
val Teal600 = Color(0xFF256F70)
val Teal100 = Color(0xFFDCEEEF)
val Ink900 = CharcoalWarm
val Ink700 = WarmGray
val BackgroundWarm = BackgroundCream
val SurfaceSoft = InputBg
val BorderSoft = WarmBorder
val SuccessTeal = SageGreen
val WarningMuted = Amber600
val BorderDark = Color(0xFF3A3834)
val Night650 = Color(0xFF3A3834)
val Teal300 = Color(0xFF72CCCB)
val Teal200 = Color(0xFFAEE5E4)
val Amber300 = Color(0xFFE7C98A)
val Amber400Legacy = Color(0xFFD3A95C)

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
    income = Color(0xFFE57373),
    expense = Color(0xFF66BB6A),
    current = Color(0xFFE5B84C),
    transfer = Color(0xFF90CAF9),
    reminder = Color(0xFFCE93D8),
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

internal val LightMoneyColorsInstance = LightMoneyColors
internal val DarkMoneyColorsInstance = DarkMoneyColors
