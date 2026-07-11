package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class DeleteTransferRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        recordId: Long,
        expectedUpdatedAt: Long? = null,
    ): LedgerUndoToken? = transactionRepository.runInTransaction {
        val existing = transactionRepository.queryStoredTransferRecordById(recordId) ?: return@runInTransaction null
        if (existing.deletedAt != null) return@runInTransaction null
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
            throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
        }
        val fromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) { "转出账户不存在" }
        val toAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) { "转入账户不存在" }
        fromAccount.requireOpenForMutation("删除转账记录")
        toAccount.requireOpenForMutation("删除转账记录")
        val affectedAccountIds = setOf(existing.fromAccountId, existing.toAccountId)
        val deletedAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.softDeleteTransferRecord(
                id = recordId,
                operationId = existing.operationId,
                expectedUpdatedAt = existing.updatedAt,
                deletedAt = deletedAt,
            )
        ) {
            throw LedgerRecordChangedException(LedgerRecordKind.TRANSFER, recordId)
        }
        affectedAccountIds.forEach {
            refreshAccountActivityStateUseCase(it)
        }
        LedgerUndoToken(
            kind = LedgerRecordKind.TRANSFER,
            recordId = recordId,
            operationId = existing.operationId,
            deletedAt = deletedAt,
        )
    }
}

