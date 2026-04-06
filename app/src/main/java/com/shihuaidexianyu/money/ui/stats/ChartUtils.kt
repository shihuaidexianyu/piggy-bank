package com.shihuaidexianyu.money.ui.stats

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

internal fun formatAxisValue(value: Float): String {
    val absVal = abs(value)
    return when {
        absVal >= 10_000f -> {
            val wan = BigDecimal.valueOf(value.toDouble()).divide(BigDecimal.valueOf(10_000), 1, RoundingMode.HALF_UP)
            "${wan.stripTrailingZeros().toPlainString()}万"
        }
        absVal >= 1f -> {
            BigDecimal.valueOf(value.toDouble()).setScale(0, RoundingMode.HALF_UP).toPlainString()
        }
        else -> {
            BigDecimal.valueOf(value.toDouble()).setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
    }
}
