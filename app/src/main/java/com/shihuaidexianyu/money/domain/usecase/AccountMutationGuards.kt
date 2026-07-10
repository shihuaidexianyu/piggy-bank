package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account

internal fun Account.requireActiveForMutation(action: String) {
    require(!isClosed) { "归档账户不能$action" }
}
