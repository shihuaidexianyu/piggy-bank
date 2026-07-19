package com.shihuaidexianyu.money.domain.model

enum class HistoryRecordType {
    CASH_FLOW,
    TRANSFER,
    BALANCE_UPDATE,
    BALANCE_ADJUSTMENT,
}

enum class HistoryAmountDirection {
    ALL,
    INCREASE,
    DECREASE,
}

data class HistoryRecordFilters(
    val keyword: String = "",
    val excludeKeyword: String = "",
    val recordTypes: Set<HistoryRecordType> = emptySet(),
    val accountId: Long? = null,
    val dateStartAt: Long? = null, // Inclusive when present.
    val dateEndAt: Long? = null, // Exclusive when present.
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val amountDirection: HistoryAmountDirection = HistoryAmountDirection.ALL,
)

/**
 * Matches SQLite's built-in LOWER behavior used by the Room history query: ASCII letters are
 * case-insensitive, while non-ASCII scripts remain literal. Keeping this normalization shared
 * prevents the in-memory repository and UI specification from applying broader Unicode folding.
 */
fun normalizeHistorySearchText(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(if (character in 'A'..'Z') character.lowercaseChar() else character)
    }
}

fun HistoryRecordFilters.requireValidAmountBounds() {
    require(minAmount == null || minAmount >= 0L) { "History minimum amount must be non-negative" }
    require(maxAmount == null || maxAmount >= 0L) { "History maximum amount must be non-negative" }
    require(minAmount == null || maxAmount == null || minAmount <= maxAmount) {
        "History minimum amount must not exceed maximum amount"
    }
}

data class HistoryPageCursor(
    val occurredAt: Long,
    val sourceOrder: Int,
    val recordId: Long,
)

data class HistoryRecord(
    val recordId: Long,
    val type: HistoryRecordType,
    val sourceOrder: Int,
    val accountId: Long,
    val relatedAccountId: Long?,
    val title: String,
    val amount: Long,
    val occurredAt: Long,
    val keywordSource: String,
) {
    val cursor: HistoryPageCursor
        get() = HistoryPageCursor(
            occurredAt = occurredAt,
            sourceOrder = sourceOrder,
            recordId = recordId,
        )
}
