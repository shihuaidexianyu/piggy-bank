package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository

class CreateSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
) {
    suspend operator fun invoke(
        name: String,
        targetAmount: Long,
        accountIds: List<Long>,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        require(name.isNotBlank()) { "目标名称不能为空" }
        require(targetAmount > 0L) { "目标金额必须大于 0" }
        return savingsGoalRepository.createGoal(
            name = name.trim(),
            targetAmount = targetAmount,
            accountIds = accountIds.distinct(),
            createdAt = createdAt,
        )
    }
}
