package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryRecurringReminderRepository : RecurringReminderRepository {
    private val reminders = MutableStateFlow<Map<Long, RecurringReminderEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeAllReminders(): Flow<List<RecurringReminderEntity>> =
        reminders.map { it.values.sortedBy { r -> r.nextDueAt } }

    override fun observeDueReminders(): Flow<List<RecurringReminderEntity>> =
        reminders.map { map ->
            val now = System.currentTimeMillis()
            map.values
                .filter { it.isEnabled && it.nextDueAt <= now }
                .sortedBy { it.nextDueAt }
        }

    override suspend fun getReminderById(id: Long): RecurringReminderEntity? = reminders.value[id]

    override suspend fun queryAll(): List<RecurringReminderEntity> =
        reminders.value.values.sortedBy { it.nextDueAt }

    override suspend fun queryDue(): List<RecurringReminderEntity> {
        val now = System.currentTimeMillis()
        return reminders.value.values
            .filter { it.isEnabled && it.nextDueAt <= now }
            .sortedBy { it.nextDueAt }
    }

    override suspend fun insertReminder(reminder: RecurringReminderEntity): Long {
        val id = nextId++
        val entity = reminder.copy(id = id)
        reminders.value = reminders.value + (id to entity)
        return id
    }

    override suspend fun updateReminder(reminder: RecurringReminderEntity) {
        reminders.value = reminders.value + (reminder.id to reminder)
    }

    override suspend fun deleteReminder(id: Long) {
        reminders.value = reminders.value - id
    }
}
