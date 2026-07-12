package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object AmountFormatter {
    private val formatters = ThreadLocal.withInitial {
        DecimalFormat(
            "#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.SIMPLIFIED_CHINESE),
        ).apply {
            roundingMode = RoundingMode.HALF_UP
            isParseBigDecimal = true
        }
    }

    fun format(
        amountInMinor: Long,
        settings: PortableSettings,
        visibility: AmountVisibility = AmountVisibility.VISIBLE,
    ): String {
        if (visibility == AmountVisibility.MASKED) return "${settings.currencySymbol}••••"
        val absolute = BigDecimal.valueOf(amountInMinor)
            .movePointLeft(2)
            .abs()
            .setScale(2, RoundingMode.HALF_UP)
        val grouped = decimalFormatter().format(absolute)
        val signed = if (amountInMinor < 0) "-" else ""
        return "${signed}${settings.currencySymbol}$grouped"
    }

    fun formatPlain(amountInMinor: Long): String {
        return decimalFormatter().format(
            BigDecimal.valueOf(amountInMinor, 2).setScale(2, RoundingMode.HALF_UP),
        )
    }

    private fun decimalFormatter(): DecimalFormat = requireNotNull(formatters.get())
}
