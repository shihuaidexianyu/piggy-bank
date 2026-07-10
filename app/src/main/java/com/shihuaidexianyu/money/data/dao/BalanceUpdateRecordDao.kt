package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceUpdateRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BalanceUpdateRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(record: BalanceUpdateRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<BalanceUpdateRecordEntity>)

    @Query(
        """
        UPDATE balance_update_records
        SET accountId = :accountId,
            actualBalance = :actualBalance,
            systemBalanceBeforeUpdate = :systemBalanceBeforeUpdate,
            delta = :delta,
            occurredAt = :occurredAt,
            updatedAt = :updatedAt
        WHERE id = :id
            AND operationId = :operationId
            AND deletedAt IS NULL
            AND updatedAt = :expectedUpdatedAt
        """,
    )
    suspend fun updateActive(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        accountId: Long,
        actualBalance: Long,
        systemBalanceBeforeUpdate: Long,
        delta: Long,
        occurredAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE balance_update_records
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id
            AND operationId = :operationId
            AND updatedAt = :expectedUpdatedAt
            AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(
        id: Long,
        operationId: String,
        expectedUpdatedAt: Long,
        deletedAt: Long,
    ): Int

    @Query(
        """
        UPDATE balance_update_records
        SET deletedAt = NULL, updatedAt = :restoredAt
        WHERE id = :id
            AND operationId = :operationId
            AND deletedAt = :expectedDeletedAt
        """,
    )
    suspend fun restore(
        id: Long,
        operationId: String,
        expectedDeletedAt: Long,
        restoredAt: Long,
    ): Int

    @Query("SELECT * FROM balance_update_records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun queryById(id: Long): BalanceUpdateRecordEntity?

    @Query("SELECT * FROM balance_update_records WHERE id = :id LIMIT 1")
    suspend fun queryStoredById(id: Long): BalanceUpdateRecordEntity?

    @Query("SELECT * FROM balance_update_records WHERE operationId = :operationId LIMIT 1")
    suspend fun queryByOperationId(operationId: String): BalanceUpdateRecordEntity?

    @Query("SELECT * FROM balance_update_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<BalanceUpdateRecordEntity>

    @Query("SELECT * FROM balance_update_records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceUpdateRecordEntity>

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryBetween(startInclusive: Long, endExclusive: Long): List<BalanceUpdateRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0) FROM balance_update_records
        WHERE deletedAt IS NULL
            AND delta > 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumPositiveDeltaBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(-delta), 0) FROM balance_update_records
        WHERE deletedAt IS NULL
            AND delta < 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumNegativeDeltaBetween(startInclusive: Long, endExclusive: Long): Long

    @Query("SELECT COUNT(*) FROM balance_update_records WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE accountId = :accountId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<BalanceUpdateRecordEntity>

    @Query(
        """
        SELECT * FROM balance_update_records
        WHERE accountId = :accountId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForAccount(accountId: Long): BalanceUpdateRecordEntity?

    @Query("DELETE FROM balance_update_records")
    suspend fun deleteAll()
}
