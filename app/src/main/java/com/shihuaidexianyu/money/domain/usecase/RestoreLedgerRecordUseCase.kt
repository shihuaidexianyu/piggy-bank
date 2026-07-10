package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class RestoreLedgerRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(token: LedgerUndoToken): RestoreLedgerResult {
        if (token.version != TOKEN_VERSION || token.operationId.isBlank()) return RestoreLedgerResult.STALE
        return transactionRepository.runInTransaction {
            when (token.kind) {
                LedgerRecordKind.CASH_FLOW -> restoreCashFlow(token)
                LedgerRecordKind.TRANSFER -> restoreTransfer(token)
                LedgerRecordKind.BALANCE_UPDATE -> restoreBalanceUpdate(token)
                LedgerRecordKind.BALANCE_ADJUSTMENT -> restoreBalanceAdjustment(token)
            }
        }
    }

    private suspend fun restoreCashFlow(token: LedgerUndoToken): RestoreLedgerResult {
        val existing = transactionRepository.queryStoredCashFlowRecordById(token.recordId)
            ?: return missingOrStale(token)
        validateStoredIdentity(existing.operationId, existing.deletedAt, token)?.let { return it }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireOpenForMutation("恢复收支记录")
        val restoredAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.restoreCashFlowRecord(
                token.recordId,
                token.operationId,
                token.deletedAt,
                restoredAt,
            )
        ) return RestoreLedgerResult.STALE
        refreshAccountActivityStateUseCase(existing.accountId)
        return RestoreLedgerResult.RESTORED
    }

    private suspend fun restoreTransfer(token: LedgerUndoToken): RestoreLedgerResult {
        val existing = transactionRepository.queryStoredTransferRecordById(token.recordId)
            ?: return missingOrStale(token)
        validateStoredIdentity(existing.operationId, existing.deletedAt, token)?.let { return it }
        val fromAccount = requireNotNull(accountRepository.getAccountById(existing.fromAccountId)) { "转出账户不存在" }
        val toAccount = requireNotNull(accountRepository.getAccountById(existing.toAccountId)) { "转入账户不存在" }
        fromAccount.requireOpenForMutation("恢复转账记录")
        toAccount.requireOpenForMutation("恢复转账记录")
        val restoredAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.restoreTransferRecord(
                token.recordId,
                token.operationId,
                token.deletedAt,
                restoredAt,
            )
        ) return RestoreLedgerResult.STALE
        setOf(existing.fromAccountId, existing.toAccountId).forEach { accountId ->
            refreshAccountActivityStateUseCase(accountId)
        }
        return RestoreLedgerResult.RESTORED
    }

    private suspend fun restoreBalanceUpdate(token: LedgerUndoToken): RestoreLedgerResult {
        val existing = transactionRepository.queryStoredBalanceUpdateRecordById(token.recordId)
            ?: return missingOrStale(token)
        validateStoredIdentity(existing.operationId, existing.deletedAt, token)?.let { return it }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireOpenForMutation("恢复余额核对")
        val restoredAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.restoreBalanceUpdateRecord(
                token.recordId,
                token.operationId,
                token.deletedAt,
                restoredAt,
            )
        ) return RestoreLedgerResult.STALE
        refreshAccountActivityStateUseCase(existing.accountId)
        return RestoreLedgerResult.RESTORED
    }

    private suspend fun restoreBalanceAdjustment(token: LedgerUndoToken): RestoreLedgerResult {
        val existing = transactionRepository.queryStoredBalanceAdjustmentRecordById(token.recordId)
            ?: return missingOrStale(token)
        validateStoredIdentity(existing.operationId, existing.deletedAt, token)?.let { return it }
        val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户不存在" }
        account.requireOpenForMutation("恢复余额调整")
        val restoredAt = nextMutationTimestamp(clockProvider.nowMillis(), existing.updatedAt)
        if (!transactionRepository.restoreBalanceAdjustmentRecord(
                token.recordId,
                token.operationId,
                token.deletedAt,
                restoredAt,
            )
        ) return RestoreLedgerResult.STALE
        refreshAccountActivityStateUseCase(existing.accountId)
        return RestoreLedgerResult.RESTORED
    }

    private fun validateStoredIdentity(
        storedOperationId: String,
        storedDeletedAt: Long?,
        token: LedgerUndoToken,
    ): RestoreLedgerResult? {
        if (storedOperationId != token.operationId) return RestoreLedgerResult.STALE
        if (storedDeletedAt == null) return RestoreLedgerResult.ALREADY_ACTIVE
        if (storedDeletedAt != token.deletedAt) return RestoreLedgerResult.STALE
        return null
    }

    private suspend fun missingOrStale(token: LedgerUndoToken): RestoreLedgerResult {
        val identityExists = transactionRepository.queryCashFlowRecordByOperationId(token.operationId) != null ||
            transactionRepository.queryTransferRecordByOperationId(token.operationId) != null ||
            transactionRepository.queryBalanceUpdateRecordByOperationId(token.operationId) != null ||
            transactionRepository.queryBalanceAdjustmentRecordByOperationId(token.operationId) != null
        return if (identityExists) RestoreLedgerResult.STALE else RestoreLedgerResult.NOT_FOUND
    }

    private companion object {
        const val TOKEN_VERSION = 1
    }
}
