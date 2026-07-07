package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository

class DeleteSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
) {
    suspend operator fun invoke(id: Long) {
        savingsGoalRepository.deleteGoal(id)
    }
}
