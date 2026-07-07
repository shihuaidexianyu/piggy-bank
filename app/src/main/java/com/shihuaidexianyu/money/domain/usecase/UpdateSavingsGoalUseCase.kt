package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository

class UpdateSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
) {
    suspend operator fun invoke(goal: SavingsGoal) {
        require(goal.targetAmount > 0L) { "目标金额必须大于 0" }
        savingsGoalRepository.updateGoal(goal)
    }
}
