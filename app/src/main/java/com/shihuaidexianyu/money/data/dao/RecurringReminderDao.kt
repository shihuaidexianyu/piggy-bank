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
        SET name = :name,
            type = :type,
            accountId = :accountId,
            direction = :direction,
            amount = :amount,
            periodType = :periodType,
            periodValue = :periodValue,
            periodMonth = :periodMonth,
            isEnabled = :isEnabled,
            nextDueAt = :nextDueAt,
            anchorDueAt = :anchorDueAt,
            lastNotifiedDueAt = CASE WHEN :clearNotificationCursor THEN NULL ELSE lastNotifiedDueAt END,
            updatedAt = :updatedAt
        WHERE id = :id AND updatedAt = :expectedUpdatedAt
        """,
    )
    suspend fun updateIfUnchanged(
        id: Long,
        name: String,
        type: String,
        accountId: Long,
        direction: String,
        amount: Long,
        periodType: String,
        periodValue: Int,
        periodMonth: Int?,
        isEnabled: Boolean,
        nextDueAt: Long,
        anchorDueAt: Long,
        updatedAt: Long,
        expectedUpdatedAt: Long,
        clearNotificationCursor: Boolean,
    ): Int

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
            AND updatedAt = :expectedUpdatedAt
        """,
    )
    suspend fun advanceOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        nextDueAt: Long,
        confirmedAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE recurring_reminders
        SET lastNotifiedDueAt = :expectedDueAt
        WHERE id = :reminderId
          AND isEnabled = 1
          AND nextDueAt = :expectedDueAt
          AND (lastNotifiedDueAt IS NULL OR lastNotifiedDueAt != :expectedDueAt)
        """,
    )
    suspend fun acknowledgeNotifiedOccurrence(reminderId: Long, expectedDueAt: Long): Int

    @Query(
        """
        UPDATE recurring_reminders
        SET nextDueAt = :advancedDueAt,
            updatedAt = :skippedUpdatedAt
        WHERE id = :reminderId
          AND isEnabled = 1
          AND nextDueAt = :expectedDueAt
          AND updatedAt = :expectedUpdatedAt
        """,
    )
    suspend fun skipOccurrence(
        reminderId: Long,
        expectedDueAt: Long,
        expectedUpdatedAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE recurring_reminders
        SET nextDueAt = :skippedDueAt,
            lastNotifiedDueAt = NULL,
            updatedAt = :restoredUpdatedAt
        WHERE id = :reminderId
          AND isEnabled = 1
          AND nextDueAt = :advancedDueAt
          AND updatedAt = :skippedUpdatedAt
        """,
    )
    suspend fun undoSkippedOccurrence(
        reminderId: Long,
        skippedDueAt: Long,
        advancedDueAt: Long,
        skippedUpdatedAt: Long,
        restoredUpdatedAt: Long,
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
