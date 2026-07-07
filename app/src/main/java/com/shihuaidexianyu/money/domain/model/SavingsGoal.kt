package com.shihuaidexianyu.money.domain.model

data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val createdAt: Long,
    val accountIds: List<Long> = emptyList(),
)

data class SavingsGoalWithProgress(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val createdAt: Long,
    val accountIds: List<Long>,
    val currentAmount: Long,
    val isAchieved: Boolean,
)
