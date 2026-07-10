package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.notification.NotificationLaunchDestination
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider

class ResolveNotificationLaunchUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) {
    suspend operator fun invoke(identity: NotificationLaunchIdentity): NotificationLaunchDestination =
        when (identity) {
            is NotificationLaunchIdentity.Recurring -> resolveRecurring(identity)
            is NotificationLaunchIdentity.Balance -> resolveBalance(identity)
        }

    private suspend fun resolveRecurring(
        identity: NotificationLaunchIdentity.Recurring,
    ): NotificationLaunchDestination {
        val reminder = reminderRepository.getReminderById(identity.reminderId)
        val account = reminder?.let { accountRepository.getAccountById(it.accountId) }
        return if (reminder != null && account != null && !account.isClosed &&
            reminder.isEnabled && reminder.nextDueAt == identity.expectedDueAt &&
            reminder.nextDueAt <= clockProvider.nowMillis()
        ) {
            NotificationLaunchDestination.ProcessReminder(reminder)
        } else {
            NotificationLaunchDestination.ReminderCenter(stateChanged = true)
        }
    }

    private suspend fun resolveBalance(
        identity: NotificationLaunchIdentity.Balance,
    ): NotificationLaunchDestination {
        val account = accountRepository.getAccountById(identity.accountId)
        val config = accountReminderSettingsRepository.queryReminderConfigs()[identity.accountId]
        val isCurrent = account != null && !account.isClosed && config != null && config.isEnabled &&
            AccountStatusCalculator.isStale(
                account = account,
                reminderConfig = config,
                nowMillis = clockProvider.nowMillis(),
                zoneId = zoneIdProvider.zoneId(),
            )
        return if (isCurrent) {
            NotificationLaunchDestination.ReconcileBalance(identity.accountId)
        } else {
            NotificationLaunchDestination.ReminderCenter(stateChanged = true)
        }
    }
}
