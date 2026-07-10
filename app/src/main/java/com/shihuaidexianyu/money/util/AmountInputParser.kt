package com.shihuaidexianyu.money.util

import java.math.BigDecimal
import java.math.RoundingMode

object AmountInputParser {
    fun parseUnsignedToMinor(text: String): Long? {
        return parseSignedToMinor(text)?.takeIf { it >= 0L }
    }

    fun parseSignedToMinor(text: String): Long? {
        val normalized = text
            .trim()
            .replace(",", "")
            .replace("＋", "+")
            .replace("－", "-")
            .replace("−", "-")
            .filterNot(Char::isWhitespace)
        if (normalized.isEmpty()) return null
        val decimal = parseExpression(normalized) ?: return null

        return runCatching {
            decimal
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    @Deprecated(
        message = "Use parseUnsignedToMinor or parseSignedToMinor explicitly",
        replaceWith = ReplaceWith("parseUnsignedToMinor(text)"),
    )
    fun parseToMinor(text: String): Long? = parseUnsignedToMinor(text)

    private fun parseExpression(text: String): BigDecimal? {
        var index = 0
        var sign = BigDecimal.ONE
        var total = BigDecimal.ZERO
        var expectingNumber = true

        while (index < text.length) {
            val char = text[index]
            if (char == '+' || char == '-') {
                if (expectingNumber && index != 0) return null
                sign = if (char == '-') BigDecimal.ONE.negate() else BigDecimal.ONE
                index += 1
                expectingNumber = true
                continue
            }

            val numberStart = index
            while (index < text.length && text[index] != '+' && text[index] != '-') {
                val current = text[index]
                if (!current.isDigit() && current != '.') return null
                index += 1
            }
            if (numberStart == index) return null
            val number = text.substring(numberStart, index).toBigDecimalOrNull() ?: return null
            total += number * sign
            expectingNumber = false
        }

        return if (expectingNumber) null else total
    }
}
