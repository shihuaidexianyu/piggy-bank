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
