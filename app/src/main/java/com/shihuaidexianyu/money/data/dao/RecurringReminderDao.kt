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

    @Update
    suspend fun update(reminder: RecurringReminderEntity)

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
}
