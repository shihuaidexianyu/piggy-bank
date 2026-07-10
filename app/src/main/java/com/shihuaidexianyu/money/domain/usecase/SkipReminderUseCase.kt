package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.NoOpNotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp

class SkipReminderUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val notificationSyncRequester: NotificationSyncRequester = NoOpNotificationSyncRequester,
) {
    suspend operator fun invoke(reminderId: Long, expectedDueAt: Long): ReminderSkipUndoToken {
        val reminder = requireNotNull(reminderRepository.getReminderById(reminderId)) { "提醒不存在" }
        check(reminder.isEnabled && reminder.nextDueAt == expectedDueAt) { "提醒状态已变化" }
        val now = clockProvider.nowMillis()
        check(expectedDueAt <= now) { "提醒尚未到期" }
        val account = requireNotNull(accountRepository.getAccountById(reminder.accountId)) { "账户不存在" }
        account.requireOpenForMutation("跳过提醒")
        val advancedDueAt = ReminderNextDueCalculator.calculateNextDue(
            currentDueAt = expectedDueAt,
            anchorDueAt = reminder.anchorDueAt,
            periodType = ReminderPeriodType.fromValue(reminder.periodType),
            periodValue = reminder.periodValue,
            periodMonth = reminder.periodMonth,
            zoneId = zoneIdProvider.zoneId(),
        )
        val skippedUpdatedAt = nextMutationTimestamp(now, reminder.updatedAt)
        check(
            reminderRepository.skipOccurrence(
                reminderId = reminderId,
                expectedDueAt = expectedDueAt,
                expectedUpdatedAt = reminder.updatedAt,
                advancedDueAt = advancedDueAt,
                skippedUpdatedAt = skippedUpdatedAt,
            ),
        ) { "提醒状态已变化" }
        runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_SKIPPED) }
        return ReminderSkipUndoToken(reminderId, expectedDueAt, advancedDueAt, skippedUpdatedAt)
    }
}
