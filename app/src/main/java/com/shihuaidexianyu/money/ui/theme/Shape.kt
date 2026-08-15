package com.shihuaidexianyu.money.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// A touch softer than the M3 defaults (4/8/12/16/28) so cards, sheets, and buttons carry a
// consistent, less generic corner language. Components already reference MaterialTheme.shapes,
// so the scale shifts everywhere through this one token set.
val MoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
