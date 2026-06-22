package com.shihuaidexianyu.money.ui.common

import androidx.compose.ui.unit.dp

/**
 * Shared dimension constants. Centralizing these makes it easy to tune the layout system-wide
 * (e.g. bottom content padding for screens that sit above the bottom navigation bar).
 */
object MoneyDimens {
    /**
     * Bottom content padding for LazyColumns on top-level screens. Large enough to clear the
     * Material 3 `NavigationBar` (~80dp) plus a comfortable scroll buffer.
     */
    val bottomNavContentPadding: androidx.compose.ui.unit.Dp = 112.dp

    /** Standard horizontal content padding for screen edges. */
    val screenHorizontalPadding: androidx.compose.ui.unit.Dp = 20.dp
}
