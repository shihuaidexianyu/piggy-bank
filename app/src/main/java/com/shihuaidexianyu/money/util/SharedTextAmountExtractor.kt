package com.shihuaidexianyu.money.util

import java.math.BigDecimal

/**
 * Extracts a monetary amount from arbitrary shared text (e.g. bank SMS, payment confirmation).
 * Looks for patterns like "¥123.45", "123.45元", "金额：100", "消费 50 元".
 * Returns the amount in minor units (cents/fen), or null if no plausible amount is found.
 */
object SharedTextAmountExtractor {
    private val amountPattern = Regex(
        "(?:¥|￥|RMB|金额[:：\\s]*)?\\s*(\\d{1,12}(?:[.,]\\d{1,2})?)\\s*(?:元|块|CNY|RMB)?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Extracts the first plausible amount from [text]. Heuristic:
     * 1. Find all matches of the amount regex.
     * 2. Prefer matches preceded by keywords like 消费/支出/付款/转账/金额/¥/￥.
     * 3. Fall back to the first match.
     * Returns cents (Long) or null.
     */
    fun extractAmountMillis(text: String): Long? {
        if (text.isBlank()) return null
        val matches = amountPattern.findAll(text).toList()
        if (matches.isEmpty()) return null

        // Prefer amounts near money keywords.
        val keywordPattern = Regex("(消费|支出|付款|支付|转账|金额|收入|到账|存入|工资|薪水|¥|￥|RMB|CNY)", RegexOption.IGNORE_CASE)
        val keywordMatch = matches.firstOrNull { match ->
            val before = text.substring(maxOf(0, match.range.first - 20), match.range.first)
            keywordPattern.containsMatchIn(before)
        }
        val target = keywordMatch ?: matches.first()
        val rawNumber = target.groupValues[1].replace(",", "")
        return runCatching {
            val decimal = BigDecimal(rawNumber).setScale(2, java.math.RoundingMode.HALF_UP)
            decimal.multiply(BigDecimal(100)).longValueExact()
        }.getOrNull()
    }
}
