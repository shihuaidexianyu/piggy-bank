package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account

internal fun Account.requireOpenForMutation(action: String) {
    require(!isClosed) { "关闭账户不能$action" }
}
