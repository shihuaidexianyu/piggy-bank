package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shihuaidexianyu.money.domain.model.SAVINGS_GOAL_ID

@Entity(
    tableName = "savings_goals",
)
data class SavingsGoalEntity(
    @PrimaryKey
    val id: Long = SAVINGS_GOAL_ID,
    val targetAmount: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
