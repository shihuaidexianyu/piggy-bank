package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isArchived"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val isArchived: Boolean = false,
    val lastUsedAt: Long? = null,
    val lastBalanceUpdateAt: Long? = null,
    val displayOrder: Int = 0,
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
)

