package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shihuaidexianyu.money.domain.model.BudgetPeriod

@Entity(tableName = "portable_settings")
data class PortableSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val currencySymbol: String,
    val amountColorMode: String,
    // The column keeps its original name for storage compatibility; the budget amount now
    // applies to whichever [BudgetPeriod] the user picked.
    val monthlyBudgetAmount: Long?,
    val budgetPeriod: String = BudgetPeriod.DEFAULT.value,
)
