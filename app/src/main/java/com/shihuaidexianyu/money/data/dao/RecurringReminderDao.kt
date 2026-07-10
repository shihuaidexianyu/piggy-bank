package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: RecurringReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reminders: List<RecurringReminderEntity>)

    @Update
    suspend fun update(reminder: RecurringReminderEntity)

    @Query(
        """
        UPDATE recurring_reminders
        SET isEnabled = 0, updatedAt = :updatedAt
        WHERE accountId = :accountId AND isEnabled = 1
        """,
    )
    suspend fun disableEnabledForAccount(accountId: Long, updatedAt: Long)

    @Query(
        """
        UPDATE recurring_reminders
        SET nextDueAt = :nextDueAt,
            lastConfirmedAt = :confirmedAt,
            updatedAt = :updatedAt
        WHERE id = :reminderId
            AND isEnabled = 1
            AND nextDueAt = :expectedDueAt
        """,
    )
    suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM recurring_reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recurring_reminders WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): RecurringReminderEntity?

    @Query("SELECT * FROM recurring_reminders ORDER BY nextDueAt ASC, id ASC")
    fun observeAll(): Flow<List<RecurringReminderEntity>>

    @Query("SELECT * FROM recurring_reminders ORDER BY nextDueAt ASC, id ASC")
    suspend fun queryAll(): List<RecurringReminderEntity>

    @Query(
        """
        SELECT * FROM recurring_reminders
        WHERE isEnabled = 1 AND nextDueAt <= :now
        ORDER BY nextDueAt ASC, id ASC
        """,
    )
    fun observeDue(now: Long): Flow<List<RecurringReminderEntity>>

    @Query(
        """
        SELECT * FROM recurring_reminders
        WHERE isEnabled = 1 AND nextDueAt <= :now
        ORDER BY nextDueAt ASC, id ASC
        """,
    )
    suspend fun queryDue(now: Long): List<RecurringReminderEntity>

    @Query("DELETE FROM recurring_reminders")
    suspend fun deleteAll()
}
