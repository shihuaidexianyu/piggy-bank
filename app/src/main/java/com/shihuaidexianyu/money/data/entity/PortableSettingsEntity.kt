package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portable_settings")
data class PortableSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val currencySymbol: String,
    val amountColorMode: String,
    val monthlyBudgetAmount: Long?,
)
