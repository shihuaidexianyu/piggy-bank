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
    val accountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val amountDirection: HistoryAmountDirection = HistoryAmountDirection.ALL,
)

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
