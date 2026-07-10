package com.shihuaidexianyu.money.util

sealed interface AmountKey {
    data class Digit(val value: Int) : AmountKey
    data object Decimal : AmountKey
    data object Plus : AmountKey
    data object Minus : AmountKey
    data object Delete : AmountKey
    data object Clear : AmountKey
}

fun appendAmountKey(
    current: String,
    key: AmountKey,
    allowSigned: Boolean = false,
): String {
    return when (key) {
        is AmountKey.Digit -> {
            if (key.value in 0..9) current + key.value.toString() else current
        }
        AmountKey.Decimal -> appendDecimal(current)
        AmountKey.Plus -> appendOperator(current, '+', allowSigned)
        AmountKey.Minus -> appendOperator(current, '-', allowSigned)
        AmountKey.Delete -> current.dropLast(1)
        AmountKey.Clear -> ""
    }
}

fun parseAmountKeypadPreview(text: String, allowSigned: Boolean): Long? {
    return if (allowSigned) {
        AmountInputParser.parseSignedToMinor(text)
    } else {
        AmountInputParser.parseUnsignedToMinor(text)
    }
}

private fun appendDecimal(current: String): String {
    val segment = current.currentAmountSegment()
    if (segment.contains('.')) return current
    return if (current.isEmpty() || current.last().isAmountOperator()) {
        current + "0."
    } else {
        current + "."
    }
}

private fun appendOperator(current: String, operator: Char, allowSigned: Boolean): String {
    if (current.isEmpty()) {
        return if (allowSigned && operator == '-') "-" else current
    }
    if (current.last().isAmountOperator()) return current
    return current + operator
}

private fun String.currentAmountSegment(): String {
    val lastOperatorIndex = indexOfLast(Char::isAmountOperator)
    return if (lastOperatorIndex < 0) this else substring(lastOperatorIndex + 1)
}

private fun Char.isAmountOperator(): Boolean = this == '+' || this == '-'
