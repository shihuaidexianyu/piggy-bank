package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account

internal fun Account.requireOpenForMutation(action: String) {
    require(!isClosed) { "${ValidationErrorText.CLOSED_ACCOUNT_PREFIX}$action" }
}
