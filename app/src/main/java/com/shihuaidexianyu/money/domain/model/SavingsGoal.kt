package com.shihuaidexianyu.money.domain.model

data class SavingsGoal(
    val id: Long,
    val targetAmount: Long,
    val createdAt: Long,
)

data class SavingsGoalWithProgress(
    val id: Long,
    val targetAmount: Long,
    val currentAmount: Long,
    val isAchieved: Boolean,
)
