package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isHidden"]),
        Index(value = ["closedAt"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val isHidden: Boolean = false,
    val closedAt: Long? = null,
    val lastUsedAt: Long? = null,
    val lastBalanceUpdateAt: Long? = null,
    val displayOrder: Int = 0,
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
)

