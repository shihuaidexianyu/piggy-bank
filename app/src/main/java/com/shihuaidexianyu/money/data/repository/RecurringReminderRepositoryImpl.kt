package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.util.minuteTickerFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RecurringReminderRepositoryImpl(
    private val dao: RecurringReminderDao,
    private val tickerFlow: Flow<Long> = minuteTickerFlow(),
) : RecurringReminderRepository {
    override fun observeAllReminders(): Flow<List<RecurringReminder>> =
        dao.observeAll().map { reminders -> reminders.map { it.toDomain() } }

    override fun observeDueReminders(): Flow<List<RecurringReminder>> =
        combine(
            dao.observeAll(),
            tickerFlow,
        ) { reminders, now ->
            reminders.filter { it.isEnabled && it.nextDueAt <= now }
                .map { it.toDomain() }
        }.distinctUntilChanged()

    override suspend fun getReminderById(id: Long): RecurringReminder? = dao.queryById(id)?.toDomain()

    override suspend fun queryAll(): List<RecurringReminder> = dao.queryAll().map { it.toDomain() }

    override suspend fun queryDue(): List<RecurringReminder> =
        dao.queryDue(System.currentTimeMillis()).map { it.toDomain() }

    override suspend fun insertReminder(reminder: RecurringReminder): Long =
        dao.insert(reminder.toEntity())

    override suspend fun updateReminder(reminder: RecurringReminder) = dao.update(reminder.toEntity())

    override suspend fun updateReminderIfUnchanged(
        reminder: RecurringReminder,
        expectedUpdatedAt: Long,
        clearNotificationCursor: Boolean,
    ): Boolean = reminder.toEntity().let { entity ->
        dao.updateIfUnchanged(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            accountId = entity.accountId,
            direction = entity.direction,
            amount = entity.amount,
            periodType = entity.periodType,
            periodValue = entity.periodValue,
            periodMonth = entity.periodMonth,
            isEnabled = entity.isEnabled,
            nextDueAt = entity.nextDueAt,
            anchorDueAt = entity.anchorDueAt,
            updatedAt = entity.updatedAt,
            expectedUpdatedAt = expectedUpdatedAt,
            clearNotificationCursor = clearNotificationCursor,
        ) == 1
    }

    override suspend fun disableEnabledForAccount(accountId: Long, updatedAt: Long) =
        dao.disableEnabledForAccount(accountId, updatedAt)

    override suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Boolean = dao.advanceOccurrence(
        reminderId = reminderId,
        expectedDueAt = expectedDueAt,
        expectedUpdatedAt = expectedUpdatedAt,
        nextDueAt = nextDueAt,
        confirmedAt = confirmedAt,
        updatedAt = updatedAt,
    ) == 1

    override suspend fun acknowledgeNotifiedOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
    ): Boolean = dao.acknowledgeNotifiedOccurrence(reminderId, expectedDueAt) == 1

    override suspend fun skipOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
    ): Boolean = dao.skipOccurrence(
        reminderId,
        expectedDueAt,
        expectedUpdatedAt,
        advancedDueAt,
        skippedUpdatedAt,
    ) == 1

    override suspend fun undoSkippedOccurrence(
        reminderId: Long,
        skippedDueAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
        restoredUpdatedAt: Long,
    ): Boolean = dao.undoSkippedOccurrence(
        reminderId,
        skippedDueAt,
        advancedDueAt,
        skippedUpdatedAt,
        restoredUpdatedAt,
    ) == 1

    override suspend fun deleteReminder(id: Long) = dao.delete(id)
}
