package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.SavingsGoalWithProgress
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveSavingsGoalsUseCase(
    private val accountRepository: AccountRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) {
    operator fun invoke(): Flow<List<SavingsGoalWithProgress>> = combine(
        savingsGoalRepository.observeAll(),
        accountRepository.observeActiveAccounts(),
        transactionRepository.observeChangeVersion(),
    ) { goals, accounts, _ ->
        if (goals.isEmpty()) {
            emptyList()
        } else {
            val balances = calculateAccountBalancesUseCase(accounts)
            val totalAssets = balances.values.sum()
            goals.map { goal ->
                SavingsGoalWithProgress(
                    id = goal.id,
                    targetAmount = goal.targetAmount,
                    currentAmount = totalAssets,
                    isAchieved = totalAssets >= goal.targetAmount,
                )
            }
        }
    }
}
