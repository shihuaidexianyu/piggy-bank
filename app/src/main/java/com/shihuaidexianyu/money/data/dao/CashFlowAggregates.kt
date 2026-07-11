package com.shihuaidexianyu.money.data.dao

data class PurposeTotalRow(
    val purpose: String,
    val amount: Long,
)

data class CashFlowDailyTotalRow(
    val epochDay: Long,
    val direction: String,
    val amount: Long,
)

data class CashFlowAnalysisEntryRow(
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val occurredAt: Long,
)

data class TransferPathTotalRow(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
)
