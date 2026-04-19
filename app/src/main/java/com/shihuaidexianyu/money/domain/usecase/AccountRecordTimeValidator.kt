package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

object AccountRecordTimeValidator {
    fun requireOccurredAtOnOrAfterAccountCreated(
        account: AccountEntity,
        occurredAt: Long,
    ) {
        val minimumOccurredAt = DateTimeTextFormatter.floorToMinute(account.createdAt)
        require(occurredAt >= minimumOccurredAt) { "时间不能早于账户创建时间" }
    }
}
