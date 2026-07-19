package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceAdjustmentRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BalanceAdjustmentRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(record: BalanceAdjustmentRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<BalanceAdjustmentRecordEntity>)

    @Query(
        """
        UPDATE balance_adjustment_records
        SET accountId = :accountId,
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
        delta: Long,
        occurredAt: Long,
        updatedAt: Long,
    ): Int

    @Query("SELECT * FROM balance_adjustment_records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun queryById(id: Long): BalanceAdjustmentRecordEntity?

    @Query("SELECT * FROM balance_adjustment_records WHERE id = :id LIMIT 1")
    suspend fun queryStoredById(id: Long): BalanceAdjustmentRecordEntity?

    @Query("SELECT * FROM balance_adjustment_records WHERE operationId = :operationId LIMIT 1")
    suspend fun queryByOperationId(operationId: String): BalanceAdjustmentRecordEntity?

    @Query("SELECT * FROM balance_adjustment_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<BalanceAdjustmentRecordEntity>

    @Query("SELECT * FROM balance_adjustment_records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceAdjustmentRecordEntity>

    @Query("SELECT COUNT(*) FROM balance_adjustment_records WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE accountId = :accountId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0) FROM balance_adjustment_records
        WHERE accountId = :accountId
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumAdjustmentBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        UPDATE balance_adjustment_records
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
        UPDATE balance_adjustment_records
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

    @Query("DELETE FROM balance_adjustment_records")
    suspend fun deleteAll()
}
