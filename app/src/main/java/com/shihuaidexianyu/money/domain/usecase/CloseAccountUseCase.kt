package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester

class CloseAccountUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val clockProvider: ClockProvider,
    private val accountLifecycleCoordinator: AccountLifecycleCoordinator,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
) {
    suspend operator fun invoke(accountId: Long) {
        accountLifecycleCoordinator.withLifecycleLock {
            transactionRunner.runInTransaction {
                val now = clockProvider.nowMillis()
                val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
                require(!account.isClosed) { "账户已关闭" }
                val balance = calculateCurrentBalanceUseCase(accountId, now)
                require(balance == 0L) { "账户余额必须为 0 才能关闭" }

                // This is one set-based mutation. A validation or query failure above leaves both
                // account and reminder state untouched; Room rolls both mutations back together.
                reminderRepository.disableEnabledForAccount(accountId, now)
                accountReminderSettingsRepository.setEnabled(accountId, false)
                accountRepository.closeAccount(accountId, now)
            }
        }
        runCatching { notificationSyncRequester.request(NotificationSyncReason.ACCOUNT_CLOSED) }
    }
}
