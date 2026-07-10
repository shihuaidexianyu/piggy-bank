package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceAdjustmentRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BalanceAdjustmentRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<BalanceAdjustmentRecordEntity>)

    @Update
    suspend fun update(record: BalanceAdjustmentRecordEntity)

    @Query("SELECT * FROM balance_adjustment_records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun queryById(id: Long): BalanceAdjustmentRecordEntity?

    @Query("SELECT * FROM balance_adjustment_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<BalanceAdjustmentRecordEntity>

    @Query("SELECT * FROM balance_adjustment_records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryBetween(startInclusive: Long, endExclusive: Long): List<BalanceAdjustmentRecordEntity>

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
        SELECT COALESCE(SUM(delta), 0) FROM balance_adjustment_records
        WHERE deletedAt IS NULL
            AND delta > 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumPositiveManualAdjustmentBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(-delta), 0) FROM balance_adjustment_records
        WHERE deletedAt IS NULL
            AND delta < 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumNegativeManualAdjustmentBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COUNT(*) FROM balance_adjustment_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """
    )
    suspend fun countBetween(startInclusive: Long, endExclusive: Long): Int

    @Query(
        """
        UPDATE balance_adjustment_records
        SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("DELETE FROM balance_adjustment_records")
    suspend fun deleteAll()
}
