package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import kotlinx.coroutines.flow.Flow

interface RecurringReminderRepository {
    fun observeAllReminders(): Flow<List<RecurringReminderEntity>>
    fun observeDueReminders(): Flow<List<RecurringReminderEntity>>
    suspend fun getReminderById(id: Long): RecurringReminderEntity?
    suspend fun queryAll(): List<RecurringReminderEntity>
    suspend fun queryDue(): List<RecurringReminderEntity>
    suspend fun insertReminder(reminder: RecurringReminderEntity): Long
    suspend fun updateReminder(reminder: RecurringReminderEntity)
    suspend fun deleteReminder(id: Long)
}
