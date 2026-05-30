package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.domain.model.Account

fun Account.toAccountOptionUiModel(): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        colorName = colorName,
        lastUsedAt = lastUsedAt,
    )
}

fun List<Account>.toAccountOptionUiModels(): List<AccountOptionUiModel> {
    return map(Account::toAccountOptionUiModel)
}

fun Account.toAccountOptionUiModel(balance: Long): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        colorName = colorName,
        balance = balance,
        lastUsedAt = lastUsedAt,
    )
}

fun Account.toAccountOptionUiModel(
    balance: Long,
    isStale: Boolean,
): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        colorName = colorName,
        balance = balance,
        lastUsedAt = lastUsedAt,
        isStale = isStale,
    )
}
