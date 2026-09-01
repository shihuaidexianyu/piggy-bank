package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class DeleteBalanceAdjustmentUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        recordId: Long,
        expectedUpdatedAt: Long? = null,
    ): LedgerUndoToken? = transactionRepository.runInTransaction {
        val existing = transactionRepository.queryStoredBalanceAdjustmentRecordById(recordId)
            ?: return@runInTransaction null
        if (existing.deletedAt != null) return@runInTransaction null
        if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
            throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_ADJUSTMENT, recordId)
        }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
        account.requireOpenForMutation("删除余额调整")
        val deletedAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.softDeleteBalanceAdjustmentRecord(
                id = recordId,
                operationId = existing.operationId,
                expectedUpdatedAt = existing.updatedAt,
                deletedAt = deletedAt,
            )
        ) {
            throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_ADJUSTMENT, recordId)
        }
        appendSyncChangesUseCase?.deleteRecord(
            entityKind = SyncEntityKind.BALANCE_ADJUSTMENT,
            recordId = recordId,
            updatedAt = deletedAt,
            deletedAt = deletedAt,
        )
        refreshAccountActivityStateUseCase(existing.accountId)
        LedgerUndoToken(
            kind = LedgerRecordKind.BALANCE_ADJUSTMENT,
            recordId = recordId,
            operationId = existing.operationId,
            deletedAt = deletedAt,
        )
    }
}
