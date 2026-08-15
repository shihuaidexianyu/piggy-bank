package com.shihuaidexianyu.money.ui.common

import androidx.compose.ui.unit.dp

/**
 * Shared dimension constants. Centralizing these makes it easy to tune the layout system-wide
 * (e.g. bottom content padding for screens that sit above the bottom navigation bar).
 */
object MoneyDimens {
    /**
     * Spacing scale tokens. New code must use these instead of ad-hoc dp literals; existing
     * literals are migrated incrementally and are deliberately not backfilled in this change.
     */
    val SpacingXs: androidx.compose.ui.unit.Dp = 4.dp
    val SpacingSm: androidx.compose.ui.unit.Dp = 8.dp
    val SpacingMd: androidx.compose.ui.unit.Dp = 12.dp
    val SpacingLg: androidx.compose.ui.unit.Dp = 16.dp
    val SpacingXl: androidx.compose.ui.unit.Dp = 24.dp

    /**
     * Bottom content padding for LazyColumns on top-level screens. Large enough to clear the
     * Material 3 `NavigationBar` (~80dp) plus a comfortable scroll buffer.
     */
    val bottomNavContentPadding: androidx.compose.ui.unit.Dp = 112.dp

    /** Standard horizontal content padding for screen edges. */
    val screenHorizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
}
