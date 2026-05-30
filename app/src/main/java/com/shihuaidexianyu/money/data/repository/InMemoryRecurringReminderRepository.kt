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

    override suspend fun deleteReminder(id: Long) {
        reminders.value = reminders.value - id
    }
}
