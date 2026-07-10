package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class DeleteBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(recordId: Long): LedgerUndoToken? = transactionRepository.runInTransaction {
        val existing = transactionRepository.queryStoredBalanceUpdateRecordById(recordId)
            ?: return@runInTransaction null
        if (existing.deletedAt != null) return@runInTransaction null
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireOpenForMutation("删除余额核对")
        val deletedAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.softDeleteBalanceUpdateRecord(
                id = recordId,
                operationId = existing.operationId,
                expectedUpdatedAt = existing.updatedAt,
                deletedAt = deletedAt,
            )
        ) {
            throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, recordId)
        }
        refreshAccountActivityStateUseCase(existing.accountId)
        LedgerUndoToken(
            kind = LedgerRecordKind.BALANCE_UPDATE,
            recordId = recordId,
            operationId = existing.operationId,
            deletedAt = deletedAt,
        )
    }
}
