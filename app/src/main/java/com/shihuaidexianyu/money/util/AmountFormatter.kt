package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.AppSettings
import java.math.BigDecimal
import java.math.RoundingMode

object AmountFormatter {
    fun format(amountInMinor: Long, settings: AppSettings): String {
        val absolute = BigDecimal.valueOf(amountInMinor)
            .movePointLeft(2)
            .abs()
            .setScale(2, RoundingMode.DOWN)
            .toPlainString()
        val grouped = addGroupingSeparators(absolute)
        val signed = if (amountInMinor < 0) "-" else ""
        return "${signed}${settings.currencySymbol}$grouped"
    }

    fun formatPlain(amountInMinor: Long): String {
        return addGroupingSeparators(
            BigDecimal.valueOf(amountInMinor, 2)
                .setScale(2, RoundingMode.DOWN)
                .toPlainString(),
        )
    }

    private fun addGroupingSeparators(decimal: String): String {
        val parts = decimal.split('.', limit = 2)
        val integerPart = parts[0]
        val groupedInteger = integerPart
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        return if (parts.size == 2) "$groupedInteger.${parts[1]}" else groupedInteger
    }
}
