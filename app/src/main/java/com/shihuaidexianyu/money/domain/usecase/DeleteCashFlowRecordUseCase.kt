package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class DeleteCashFlowRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(
        recordId: Long,
        expectedUpdatedAt: Long? = null,
    ): LedgerUndoToken? = transactionRepository.runInTransaction {
        val existing = transactionRepository.queryStoredCashFlowRecordById(recordId) ?: return@runInTransaction null
        if (existing.deletedAt != null) return@runInTransaction null
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
            throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, recordId)
        }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireOpenForMutation("删除收支记录")
        val deletedAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.softDeleteCashFlowRecord(
                id = recordId,
                operationId = existing.operationId,
                expectedUpdatedAt = existing.updatedAt,
                deletedAt = deletedAt,
            )
        ) {
            throw LedgerRecordChangedException(LedgerRecordKind.CASH_FLOW, recordId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
        LedgerUndoToken(
            kind = LedgerRecordKind.CASH_FLOW,
            recordId = recordId,
            operationId = existing.operationId,
            deletedAt = deletedAt,
        )
    }
}

