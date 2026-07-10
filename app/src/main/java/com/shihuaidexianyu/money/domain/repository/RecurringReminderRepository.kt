package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.RecurringReminder
import kotlinx.coroutines.flow.Flow

interface RecurringReminderRepository {
    fun observeAllReminders(): Flow<List<RecurringReminder>>
    fun observeDueReminders(): Flow<List<RecurringReminder>>
    suspend fun getReminderById(id: Long): RecurringReminder?
    suspend fun queryAll(): List<RecurringReminder>
    suspend fun queryDue(): List<RecurringReminder>
    suspend fun insertReminder(reminder: RecurringReminder): Long
    suspend fun updateReminder(reminder: RecurringReminder)
    suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Boolean
    suspend fun deleteReminder(id: Long)
}
