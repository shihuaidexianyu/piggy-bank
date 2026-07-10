package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner

class UpdateAccountDisplayOrderUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRunner: DatabaseTransactionRunner,
) {
    suspend operator fun invoke(orderedAccountIds: List<Long>) {
        transactionRunner.runInTransaction {
            val openAccounts = accountRepository.queryOpenAccounts()
            val openAccountIds = openAccounts.map { it.id }

            require(orderedAccountIds.size == orderedAccountIds.distinct().size) { "账户顺序不能包含重复项" }
            require(orderedAccountIds.toSet() == openAccountIds.toSet()) { "账户顺序必须覆盖全部开放账户" }

            val accountById = openAccounts.associateBy { it.id }
            orderedAccountIds.forEachIndexed { index, accountId ->
                val account = requireNotNull(accountById[accountId]) { "账户不存在" }
                account.requireOpenForMutation("调整顺序")
                if (account.displayOrder != index) {
                    accountRepository.updateAccount(account.copy(displayOrder = index))
                }
            }
        }
    }
}

