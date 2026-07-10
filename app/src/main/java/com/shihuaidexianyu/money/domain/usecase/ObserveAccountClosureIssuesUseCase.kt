package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.AccountClosureIssue
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ObserveAccountClosureIssuesUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
) {
    operator fun invoke(): Flow<List<AccountClosureIssue>> = combine(
        accountRepository.observeClosedAccounts(),
        transactionRepository.observeChangeVersion(),
    ) { accounts, _ -> accounts }
        .map { accounts ->
            val balances = calculateAccountBalancesUseCase(accounts)
            accounts.mapNotNull { account ->
                val balance = balances.getValue(account.id)
                if (balance == 0L) {
                    null
                } else {
                    AccountClosureIssue(
                        accountId = account.id,
                        accountName = account.name,
                        balance = balance,
                        closedAt = requireNotNull(account.closedAt),
                    )
                }
            }
        }
}
