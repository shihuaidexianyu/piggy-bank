package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountReminderConfigDao {
    @Query("SELECT * FROM account_reminder_configs ORDER BY accountId ASC")
    fun observeAll(): Flow<List<AccountReminderConfigEntity>>

    @Query("SELECT * FROM account_reminder_configs WHERE accountId = :accountId LIMIT 1")
    suspend fun queryByAccountId(accountId: Long): AccountReminderConfigEntity?

    @Query("SELECT * FROM account_reminder_configs ORDER BY accountId ASC")
    suspend fun queryAll(): List<AccountReminderConfigEntity>

    @Upsert
    suspend fun upsert(config: AccountReminderConfigEntity)

    @Upsert
    suspend fun upsertAll(configs: List<AccountReminderConfigEntity>)

    @Query("UPDATE account_reminder_configs SET isEnabled = :enabled WHERE accountId = :accountId")
    suspend fun updateEnabled(accountId: Long, enabled: Boolean): Int

    @Query(
        """
        UPDATE account_reminder_configs
        SET lastNotifiedBoundaryAt = :newValue
        WHERE accountId = :accountId
          AND isEnabled = 1
          AND ((lastNotifiedBoundaryAt IS NULL AND :expected IS NULL) OR lastNotifiedBoundaryAt = :expected)
        """,
    )
    suspend fun compareAndSetLastNotifiedBoundary(
        accountId: Long,
        expected: Long?,
        newValue: Long,
    ): Int

    @Query("UPDATE account_reminder_configs SET lastNotifiedBoundaryAt = NULL WHERE accountId = :accountId")
    suspend fun resetLastNotifiedBoundary(accountId: Long): Int

    @Query("DELETE FROM account_reminder_configs")
    suspend fun deleteAll()
}
