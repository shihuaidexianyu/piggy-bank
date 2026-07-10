package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.SavingsGoalProgress
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveSavingsGoalUseCase(
    private val accountRepository: AccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) {
    operator fun invoke(): Flow<SavingsGoalProgress?> = combine(
        savingsGoalRepository.observe(),
        accountRepository.observeAllAccounts(),
        transactionRepository.observeChangeVersion(),
    ) { goal, accounts, _ ->
        if (goal == null) {
            null
        } else {
            val totalAssets = calculateAccountBalancesUseCase(accounts).values.ledgerSumExact()
            SavingsGoalProgress(
                targetAmount = goal.targetAmount,
                currentAmount = totalAssets,
                isAchieved = totalAssets >= goal.targetAmount,
            )
        }
    }
}
