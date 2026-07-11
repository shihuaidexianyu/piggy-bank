package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp

class UndoSkipReminderUseCase(
    private val reminderRepository: RecurringReminderRepository,
    private val clockProvider: ClockProvider,
    private val notificationSyncRequester: NotificationSyncRequester,
) {
    suspend operator fun invoke(token: ReminderSkipUndoToken): UndoReminderSkipResult {
        val before = reminderRepository.getReminderById(token.reminderId)
        if (before == null) {
            return UndoReminderSkipResult.NOT_FOUND
        }
        if (before.isEnabled && before.nextDueAt == token.skippedDueAt && before.updatedAt != token.skippedUpdatedAt) {
            runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_UNDO) }
            return UndoReminderSkipResult.ALREADY_RESTORED
        }
        val restored = reminderRepository.undoSkippedOccurrence(
            reminderId = token.reminderId,
            skippedDueAt = token.skippedDueAt,
            advancedDueAt = token.advancedDueAt,
            skippedUpdatedAt = token.skippedUpdatedAt,
            restoredUpdatedAt = nextMutationTimestamp(clockProvider.nowMillis(), token.skippedUpdatedAt),
        )
        if (!restored) {
            val after = reminderRepository.getReminderById(token.reminderId)
            if (after != null && after.isEnabled && after.nextDueAt == token.skippedDueAt &&
                after.updatedAt != token.skippedUpdatedAt
            ) {
                runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_UNDO) }
                return UndoReminderSkipResult.ALREADY_RESTORED
            }
            return UndoReminderSkipResult.STALE
        }
        runCatching { notificationSyncRequester.request(NotificationSyncReason.REMINDER_UNDO) }
        return UndoReminderSkipResult.RESTORED
    }
}
