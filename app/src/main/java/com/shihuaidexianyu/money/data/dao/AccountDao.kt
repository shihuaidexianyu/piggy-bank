package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayOrder ASC, createdAt ASC")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun queryActiveAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 1 ORDER BY archivedAt DESC, createdAt DESC")
    fun observeArchivedAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 1 ORDER BY archivedAt DESC, createdAt DESC")
    suspend fun queryArchivedAccounts(): List<AccountEntity>

    @Query("UPDATE accounts SET lastUsedAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastUsedAt(accountId: Long, timestamp: Long)

    @Query("UPDATE accounts SET lastBalanceUpdateAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?)

    @Query(
        """
        UPDATE accounts
        SET archivedAt = :archivedAt, isArchived = 1
        WHERE id = :accountId
        """,
    )
    suspend fun archiveAccount(accountId: Long, archivedAt: Long)

    @Query(
        """
        SELECT COUNT(*) FROM accounts
        WHERE isArchived = 0 AND name = :name AND (:excludeId < 0 OR id != :excludeId)
        """,
    )
    suspend fun countActiveAccountsByName(name: String, excludeId: Long = -1): Int

    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM accounts WHERE isArchived = 0")
    suspend fun nextDisplayOrder(): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

