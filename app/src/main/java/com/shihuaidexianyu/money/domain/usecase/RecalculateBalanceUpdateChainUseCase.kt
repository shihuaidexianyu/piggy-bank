package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

class RecalculateBalanceUpdateChainUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(accountId: Long) {
        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val updates = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .sortedWith(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })

        var anchorBalance = account.initialBalance
        var anchorTime = (DateTimeTextFormatter.floorToMinute(account.createdAt) - 1L).coerceAtLeast(-1L)

        updates.forEach { record ->
            val systemBalanceBeforeUpdate = anchorBalance +
                transactionRepository.sumInflowBetween(accountId, anchorTime, record.occurredAt) -
                transactionRepository.sumOutflowBetween(accountId, anchorTime, record.occurredAt) +
                transactionRepository.sumTransferInBetween(accountId, anchorTime, record.occurredAt) -
                transactionRepository.sumTransferOutBetween(accountId, anchorTime, record.occurredAt) +
                transactionRepository.sumAdjustmentBetween(accountId, anchorTime, record.occurredAt)

            val recalculated = record.copy(
                systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
                delta = record.actualBalance - systemBalanceBeforeUpdate,
            )
            if (recalculated != record) {
                transactionRepository.updateBalanceUpdateRecord(recalculated)
            }

            anchorBalance = recalculated.actualBalance
            anchorTime = recalculated.occurredAt
        }
    }
}
