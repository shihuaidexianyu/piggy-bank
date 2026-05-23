package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.data.entity.AccountEntity

fun AccountEntity.toAccountOptionUiModel(): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        lastUsedAt = lastUsedAt,
    )
}

fun List<AccountEntity>.toAccountOptionUiModels(): List<AccountOptionUiModel> {
    return map(AccountEntity::toAccountOptionUiModel)
}

fun AccountEntity.toAccountOptionUiModel(balance: Long): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        balance = balance,
        lastUsedAt = lastUsedAt,
    )
}

