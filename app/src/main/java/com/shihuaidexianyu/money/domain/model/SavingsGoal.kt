package com.shihuaidexianyu.money.domain.model

const val SAVINGS_GOAL_ID = 1L

data class SavingsGoal(
    val id: Long = SAVINGS_GOAL_ID,
    val targetAmount: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SavingsGoalWithProgress(
    val id: Long,
    val targetAmount: Long,
    val currentAmount: Long,
    val isAchieved: Boolean,
)
