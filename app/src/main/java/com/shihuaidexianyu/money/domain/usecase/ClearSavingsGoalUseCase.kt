package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository

class ClearSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
) {
    suspend operator fun invoke() {
        savingsGoalRepository.clear()
    }
}
