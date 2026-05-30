package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

data class BalanceUpdateContext(
    val systemBalanceBeforeUpdate: Long,
    val previousUpdate: BalanceUpdateRecord?,
)

class ResolveBalanceUpdateContextUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        occurredAt: Long,
        excludingRecordId: Long? = null,
    ): BalanceUpdateContext {
        val account = requireNotNull(accountRepository.getAccountById(accountId))
        val previousUpdate = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .filter { it.id != excludingRecordId && it.occurredAt <= occurredAt }
            .maxWithOrNull(compareBy<BalanceUpdateRecord> { it.occurredAt }.thenBy { it.id })

        val anchorBalance = previousUpdate?.actualBalance ?: account.initialBalance
        val anchorTime = previousUpdate?.occurredAt
            ?: DateTimeTextFormatter.floorToMinute(account.createdAt) - 1L

        val inflow = transactionRepository.sumInflowBetween(accountId, anchorTime, occurredAt)
        val outflow = transactionRepository.sumOutflowBetween(accountId, anchorTime, occurredAt)
        val transferIn = transactionRepository.sumTransferInBetween(accountId, anchorTime, occurredAt)
        val transferOut = transactionRepository.sumTransferOutBetween(accountId, anchorTime, occurredAt)
        val adjustment = transactionRepository.sumAdjustmentBetween(accountId, anchorTime, occurredAt)

        return BalanceUpdateContext(
            systemBalanceBeforeUpdate = anchorBalance + inflow - outflow + transferIn - transferOut + adjustment,
            previousUpdate = previousUpdate,
        )
    }
}
