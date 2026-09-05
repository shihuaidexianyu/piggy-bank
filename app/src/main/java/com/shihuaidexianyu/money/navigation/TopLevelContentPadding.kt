package com.shihuaidexianyu.money.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Navigation chrome belongs to top-level pages, not the transitioning NavHost viewport. */
internal val LocalTopLevelContentPadding = staticCompositionLocalOf { PaddingValues(0.dp) }
