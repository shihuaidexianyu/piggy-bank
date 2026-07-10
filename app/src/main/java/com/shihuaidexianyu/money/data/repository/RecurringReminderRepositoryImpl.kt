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

    override suspend fun disableEnabledForAccount(accountId: Long, updatedAt: Long) =
        dao.disableEnabledForAccount(accountId, updatedAt)

    override suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Boolean = dao.advanceOccurrence(
        reminderId = reminderId,
        expectedDueAt = expectedDueAt,
        nextDueAt = nextDueAt,
        confirmedAt = confirmedAt,
        updatedAt = updatedAt,
    ) == 1

    override suspend fun deleteReminder(id: Long) = dao.delete(id)
}
