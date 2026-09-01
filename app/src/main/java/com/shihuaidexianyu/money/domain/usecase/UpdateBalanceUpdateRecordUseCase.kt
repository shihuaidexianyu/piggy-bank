package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester

class UpdateBalanceUpdateRecordUseCase(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val resolveBalanceUpdateContextUseCase: ResolveBalanceUpdateContextUseCase,
    private val refreshAccountActivityStateUseCase: RefreshAccountActivityStateUseCase,
    private val clockProvider: ClockProvider,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        recordId: Long,
        actualBalance: Long,
        occurredAt: Long,
        expectedUpdatedAt: Long? = null,
    ) {
        val now = clockProvider.nowMillis()
        require(occurredAt <= now) { ValidationErrorText.OCCURRED_AT_IN_FUTURE }

        transactionRepository.runInTransaction {
            val existing = requireNotNull(transactionRepository.getBalanceUpdateRecordById(recordId)) {
                "余额核对记录${ValidationErrorText.NOT_FOUND_SUFFIX}"
            }
            if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, recordId)
            }
            val account = requireNotNull(accountRepository.getAccountById(existing.accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
            account.requireOpenForMutation("修改余额核对")
            AccountRecordTimeValidator.requireOccurredAtOnOrAfterAccountCreated(account, occurredAt)
            val context = resolveBalanceUpdateContextUseCase(
                accountId = existing.accountId,
                occurredAt = occurredAt,
                excludingRecordId = recordId,
            )
            val updated = existing.copy(
                actualBalance = actualBalance,
                systemBalanceBeforeUpdate = context.systemBalanceBeforeUpdate,
                delta = ledgerSubtractExact(actualBalance, context.systemBalanceBeforeUpdate),
                occurredAt = occurredAt,
                updatedAt = nextMutationTimestamp(now, existing.updatedAt),
            )
            if (!transactionRepository.updateBalanceUpdateRecord(updated, existing.updatedAt)) {
                throw LedgerRecordChangedException(LedgerRecordKind.BALANCE_UPDATE, recordId)
            }
            appendSyncChangesUseCase?.upsertBalanceUpdate(updated)
            refreshAccountActivityStateUseCase(existing.accountId)
        }
        runCatching { notificationSyncRequester.request(NotificationSyncReason.BALANCE_RECONCILED) }
    }
}
