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

enum class HistoryBusinessSemantic(val value: String) {
    ALL("all"),
    DAILY_EXPENSE("daily_expense"),
    INVESTMENT_PNL("investment_pnl"),
    INVESTMENT_GAIN("investment_gain"),
    INVESTMENT_LOSS("investment_loss"),
    ;

    companion object {
        fun fromValue(value: String?): HistoryBusinessSemantic =
            entries.firstOrNull { it.value == value } ?: ALL
    }
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
    val businessSemantic: HistoryBusinessSemantic = HistoryBusinessSemantic.ALL,
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
    /** Display name of [accountId] resolved by the data layer; "" when the account is missing. */
    val accountName: String = "",
    /** Transfer counterparty name; null for non-transfer records or a missing account. */
    val relatedAccountName: String? = null,
    /**
     * Book balance of [accountId] immediately before this record: [balanceAfter] minus this
     * row's signed contribution (`amount`; for transfers the outgoing leg is `-amount`). Computed
     * over the full ledger — history filters and pagination never change it.
     */
    val balanceBefore: Long? = null,
    /**
     * Book balance of [accountId] immediately after this record, computed over the full ledger —
     * history filters and pagination never change it. TRANSFER rows carry the FROM account's
     * pair here; the receiving account's pair is on [relatedBalanceBefore]/[relatedBalanceAfter].
     */
    val balanceAfter: Long? = null,
    /** Receiving account's balance right before a TRANSFER record; null for other types. */
    val relatedBalanceBefore: Long? = null,
    /** Receiving account's balance right after a TRANSFER record; null for other types. */
    val relatedBalanceAfter: Long? = null,
) {
    val cursor: HistoryPageCursor
        get() = HistoryPageCursor(
            occurredAt = occurredAt,
            sourceOrder = sourceOrder,
            recordId = recordId,
        )
}
