package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.domain.model.Account

fun Account.toAccountOptionUiModel(): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        colorName = colorName,
        iconName = iconName,
        lastUsedAt = lastUsedAt,
        isHidden = isHidden,
        isInvestment = isInvestment,
        lastBalanceUpdateAt = lastBalanceUpdateAt,
    )
}

fun List<Account>.toAccountOptionUiModels(): List<AccountOptionUiModel> {
    return map(Account::toAccountOptionUiModel)
}

fun Account.toAccountOptionUiModel(balance: Long): AccountOptionUiModel {
    return toAccountOptionUiModel().copy(balance = balance)
}

fun Account.toAccountOptionUiModel(
    balance: Long,
    isStale: Boolean,
): AccountOptionUiModel {
    return toAccountOptionUiModel().copy(balance = balance, isStale = isStale)
}
