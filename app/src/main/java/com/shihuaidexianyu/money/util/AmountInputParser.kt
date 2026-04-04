package com.shihuaidexianyu.money.util

import java.math.RoundingMode

object AmountInputParser {
    fun parseToMinor(text: String): Long? {
        val normalized = text.trim().replace(",", "")
        if (normalized.isEmpty()) return null
        if (normalized.startsWith("-")) return null

        return runCatching {
            normalized.toBigDecimalOrNull()
                ?.setScale(2, RoundingMode.DOWN)
                ?.movePointRight(2)
                ?.longValueExact()
        }.getOrNull()
    }
}
