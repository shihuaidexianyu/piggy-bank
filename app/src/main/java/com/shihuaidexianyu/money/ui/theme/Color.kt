package com.shihuaidexianyu.money.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Teal700 = Color(0xFF1E5D5E)
val Teal600 = Color(0xFF256F70)
val Teal100 = Color(0xFFDCEEEF)
val Ink900 = Color(0xFF152027)
val Ink700 = Color(0xFF485762)
val BackgroundWarm = Color(0xFFF6F5F2)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceSoft = Color(0xFFF1F4F6)
val BorderSoft = Color(0xFFD7DEE4)
val SuccessTeal = Color(0xFF2D7B73)
val WarningMuted = Color(0xFF9A7C45)
val Night950 = Color(0xFF0E151A)
val BorderDark = Color(0xFF2F3B43)
val Night700 = Color(0xFF28353D)
val Night650 = Color(0xFF304049)
val Teal300 = Color(0xFF72CCCB)
val Teal200 = Color(0xFFAEE5E4)
val Amber400 = Color(0xFFD3A95C)
val Amber300 = Color(0xFFE7C98A)

data class MoneyColors(
    val income: Color,
    val expense: Color,
    val current: Color,
)

private val LightMoneyColors = MoneyColors(
    income = Color(0xFFC24A4A),
    expense = Color(0xFF3F8A63),
    current = Color(0xFF0F766E),
)

private val DarkMoneyColors = MoneyColors(
    income = Color(0xFFE57373),
    expense = Color(0xFF66BB6A),
    current = Color(0xFF5EEAD4),
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

internal val LightMoneyColorsInstance = LightMoneyColors
internal val DarkMoneyColorsInstance = DarkMoneyColors
