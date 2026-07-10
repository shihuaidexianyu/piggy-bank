package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.util.minuteTickerFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class InMemoryRecurringReminderRepository(
    private val tickerFlow: Flow<Long> = minuteTickerFlow(),
) : RecurringReminderRepository {
    private val reminders = MutableStateFlow<Map<Long, RecurringReminder>>(emptyMap())
    private var nextId = 1L
    private val mutationLock = Any()

    override fun observeAllReminders(): Flow<List<RecurringReminder>> =
        reminders.map { it.values.sortedBy { r -> r.nextDueAt } }

    override fun observeDueReminders(): Flow<List<RecurringReminder>> =
        combine(
            reminders,
            tickerFlow,
        ) { map, now ->
            map.values
                .filter { it.isEnabled && it.nextDueAt <= now }
                .sortedBy { it.nextDueAt }
        }.distinctUntilChanged()

    override suspend fun getReminderById(id: Long): RecurringReminder? = reminders.value[id]

    override suspend fun queryAll(): List<RecurringReminder> =
        reminders.value.values.sortedBy { it.nextDueAt }

    override suspend fun queryDue(): List<RecurringReminder> {
        val now = System.currentTimeMillis()
        return reminders.value.values
            .filter { it.isEnabled && it.nextDueAt <= now }
            .sortedBy { it.nextDueAt }
    }

    override suspend fun insertReminder(reminder: RecurringReminder): Long {
        val id = nextId++
        val entity = reminder.copy(id = id)
        reminders.value = reminders.value + (id to entity)
        return id
    }

    override suspend fun updateReminder(reminder: RecurringReminder) {
        reminders.value = reminders.value + (reminder.id to reminder)
    }

    override suspend fun updateReminderIfUnchanged(
        reminder: RecurringReminder,
        expectedUpdatedAt: Long,
        clearNotificationCursor: Boolean,
    ): Boolean = synchronized(mutationLock) {
        val existing = reminders.value[reminder.id] ?: return@synchronized false
        if (existing.updatedAt != expectedUpdatedAt) return@synchronized false
        reminders.value = reminders.value + (
            reminder.id to reminder.copy(
                lastNotifiedDueAt = if (clearNotificationCursor) null else existing.lastNotifiedDueAt,
                lastConfirmedAt = existing.lastConfirmedAt,
                createdAt = existing.createdAt,
            )
        )
        true
    }

    override suspend fun disableEnabledForAccount(accountId: Long, updatedAt: Long) {
        reminders.value = reminders.value.mapValues { (_, reminder) ->
            if (reminder.accountId == accountId && reminder.isEnabled) {
                reminder.copy(isEnabled = false, updatedAt = updatedAt)
            } else {
                reminder
            }
        }
    }

    override suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Boolean = synchronized(mutationLock) {
        val existing = reminders.value[reminderId]
            ?: return@synchronized false
        if (!existing.isEnabled || existing.nextDueAt != expectedDueAt ||
            existing.updatedAt != expectedUpdatedAt
        ) {
            return@synchronized false
        }
        reminders.value = reminders.value + (
            reminderId to existing.copy(
                nextDueAt = nextDueAt,
                lastConfirmedAt = confirmedAt,
                updatedAt = updatedAt,
            )
        )
        true
    }

    override suspend fun acknowledgeNotifiedOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
    ): Boolean = synchronized(mutationLock) {
        val existing = reminders.value[reminderId] ?: return@synchronized false
        if (!existing.isEnabled ||
            existing.nextDueAt != expectedDueAt ||
            existing.lastNotifiedDueAt == expectedDueAt
        ) {
            return@synchronized false
        }
        reminders.value = reminders.value + (
            reminderId to existing.copy(lastNotifiedDueAt = expectedDueAt)
        )
        true
    }

    override suspend fun skipOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
    ): Boolean = synchronized(mutationLock) {
        val existing = reminders.value[reminderId] ?: return@synchronized false
        if (!existing.isEnabled || existing.nextDueAt != expectedDueAt ||
            existing.updatedAt != expectedUpdatedAt
        ) return@synchronized false
        reminders.value = reminders.value + (
            reminderId to existing.copy(nextDueAt = advancedDueAt, updatedAt = skippedUpdatedAt)
        )
        true
    }

    override suspend fun undoSkippedOccurrence(
        reminderId: Long,
        skippedDueAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
        restoredUpdatedAt: Long,
    ): Boolean = synchronized(mutationLock) {
        val existing = reminders.value[reminderId] ?: return@synchronized false
        if (!existing.isEnabled || existing.nextDueAt != advancedDueAt ||
            existing.updatedAt != skippedUpdatedAt
        ) return@synchronized false
        reminders.value = reminders.value + (
            reminderId to existing.copy(
                nextDueAt = skippedDueAt,
                lastNotifiedDueAt = null,
                updatedAt = restoredUpdatedAt,
            )
        )
        true
    }

    override suspend fun deleteReminder(id: Long) {
        reminders.value = reminders.value - id
    }
}
