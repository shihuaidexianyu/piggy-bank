package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class UpsertSavingsGoalUseCase(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(targetAmount: Long) {
        require(targetAmount > 0L) { "目标金额必须大于 0" }
        val now = clockProvider.nowMillis()
        savingsGoalRepository.upsert(targetAmount, now)
    }
}
