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

    @Query("SELECT * FROM accounts ORDER BY displayOrder ASC, createdAt ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL ORDER BY displayOrder ASC, createdAt ASC")
    fun observeOpenAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL AND isHidden = 0 ORDER BY displayOrder ASC, createdAt ASC")
    fun observeVisibleOpenAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL AND isHidden = 1 ORDER BY displayOrder ASC, createdAt ASC")
    fun observeHiddenOpenAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE closedAt IS NOT NULL ORDER BY closedAt DESC, createdAt DESC")
    fun observeClosedAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun queryAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun queryOpenAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL AND isHidden = 0 ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun queryVisibleOpenAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE closedAt IS NULL AND isHidden = 1 ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun queryHiddenOpenAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE closedAt IS NOT NULL ORDER BY closedAt DESC, createdAt DESC")
    suspend fun queryClosedAccounts(): List<AccountEntity>

    @Query("UPDATE accounts SET lastUsedAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastUsedAt(accountId: Long, timestamp: Long)

    @Query("UPDATE accounts SET lastBalanceUpdateAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?)

    @Query(
        """
        UPDATE accounts
        SET isHidden = :hidden
        WHERE id = :accountId AND closedAt IS NULL
        """,
    )
    suspend fun setHidden(accountId: Long, hidden: Boolean)

    @Query("UPDATE accounts SET closedAt = :closedAt WHERE id = :accountId AND closedAt IS NULL")
    suspend fun closeAccount(accountId: Long, closedAt: Long)

    @Query("UPDATE accounts SET closedAt = NULL WHERE id = :accountId")
    suspend fun reopenAccount(accountId: Long)

    @Query(
        """
        SELECT COUNT(*) FROM accounts
        WHERE closedAt IS NULL AND name = :name AND (:excludeId < 0 OR id != :excludeId)
        """,
    )
    suspend fun countOpenAccountsByName(name: String, excludeId: Long = -1): Int

    @Query("SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM accounts WHERE closedAt IS NULL")
    suspend fun nextDisplayOrder(): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}

