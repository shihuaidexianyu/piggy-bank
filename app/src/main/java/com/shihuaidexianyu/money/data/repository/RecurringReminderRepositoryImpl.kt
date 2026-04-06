package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import kotlinx.coroutines.flow.Flow

class RecurringReminderRepositoryImpl(
    private val dao: RecurringReminderDao,
) : RecurringReminderRepository {
    override fun observeAllReminders(): Flow<List<RecurringReminderEntity>> = dao.observeAll()

    override fun observeDueReminders(): Flow<List<RecurringReminderEntity>> =
        dao.observeDue(System.currentTimeMillis())

    override suspend fun getReminderById(id: Long): RecurringReminderEntity? = dao.queryById(id)

    override suspend fun queryAll(): List<RecurringReminderEntity> = dao.queryAll()

    override suspend fun queryDue(): List<RecurringReminderEntity> =
        dao.queryDue(System.currentTimeMillis())

    override suspend fun insertReminder(reminder: RecurringReminderEntity): Long =
        dao.insert(reminder)

    override suspend fun updateReminder(reminder: RecurringReminderEntity) = dao.update(reminder)

    override suspend fun deleteReminder(id: Long) = dao.delete(id)
}
