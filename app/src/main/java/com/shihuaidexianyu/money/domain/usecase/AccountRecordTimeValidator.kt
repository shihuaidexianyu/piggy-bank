package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.TimeMath

object AccountRecordTimeValidator {
    fun requireOccurredAtOnOrAfterAccountCreated(
        account: Account,
        occurredAt: Long,
    ) {
        val minimumOccurredAt = TimeMath.floorToMinute(account.createdAt)
        require(occurredAt >= minimumOccurredAt) { ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION }
    }
}
