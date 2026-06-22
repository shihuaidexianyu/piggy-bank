package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.TimeMath

object AccountRecordTimeValidator {
    fun requireOccurredAtOnOrAfterAccountCreated(
        account: Account,
        occurredAt: Long,
    ) {
        val minimumOccurredAt = TimeMath.floorToMinute(account.createdAt)
        require(occurredAt >= minimumOccurredAt) { "时间不能早于账户创建时间" }
    }
}
