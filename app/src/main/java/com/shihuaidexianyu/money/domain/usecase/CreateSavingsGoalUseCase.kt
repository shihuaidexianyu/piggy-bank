package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository

class CreateSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
) {
    suspend operator fun invoke(
        targetAmount: Long,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        require(targetAmount > 0L) { "目标金额必须大于 0" }
        return savingsGoalRepository.createGoal(
            targetAmount = targetAmount,
            createdAt = createdAt,
        )
    }
}
