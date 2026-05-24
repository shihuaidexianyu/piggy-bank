package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.data.entity.AccountEntity

fun AccountEntity.toAccountOptionUiModel(): AccountOptionUiModel {
    return AccountOptionUiModel(
        id = id,
        name = name,
        colorName = colorName,
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
        colorName = colorName,
        balance = balance,
        lastUsedAt = lastUsedAt,
    )
}

fun AccountEntity.toAccountOptionUiModel(
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

