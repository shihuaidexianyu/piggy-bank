package com.shihuaidexianyu.money.domain.model

data class AccountClosureIssue(
    val accountId: Long,
    val accountName: String,
    val balance: Long,
    val closedAt: Long,
)
