package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_goals",
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val targetAmount: Long,
    val createdAt: Long,
)
