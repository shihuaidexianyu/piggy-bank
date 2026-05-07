package com.shihuaidexianyu.money.util

import java.math.RoundingMode

object AmountInputParser {
    fun parseToMinor(text: String): Long? {
        val normalized = text.trim().replace(",", "")
        if (normalized.isEmpty()) return null
        val decimal = normalized.toBigDecimalOrNull() ?: return null
        if (decimal.signum() < 0) return null

        return runCatching {
            decimal
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }
}
