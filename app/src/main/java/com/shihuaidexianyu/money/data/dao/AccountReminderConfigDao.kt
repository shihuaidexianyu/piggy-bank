package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity

@Dao
interface AccountReminderConfigDao {
    @Query("SELECT * FROM account_reminder_configs WHERE accountId = :accountId LIMIT 1")
    suspend fun queryByAccountId(accountId: Long): AccountReminderConfigEntity?

    @Query("SELECT * FROM account_reminder_configs ORDER BY accountId ASC")
    suspend fun queryAll(): List<AccountReminderConfigEntity>

    @Upsert
    suspend fun upsert(config: AccountReminderConfigEntity)

    @Upsert
    suspend fun upsertAll(configs: List<AccountReminderConfigEntity>)

    @Query("DELETE FROM account_reminder_configs")
    suspend fun deleteAll()
}
