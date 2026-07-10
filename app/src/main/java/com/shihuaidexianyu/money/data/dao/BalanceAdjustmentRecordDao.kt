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

    @Query("SELECT * FROM balance_adjustment_records WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): BalanceAdjustmentRecordEntity?

    @Query("SELECT * FROM balance_adjustment_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryBetween(startInclusive: Long, endExclusive: Long): List<BalanceAdjustmentRecordEntity>

    @Query("SELECT COUNT(*) FROM balance_adjustment_records")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE accountId = :accountId
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0) FROM balance_adjustment_records
        WHERE accountId = :accountId
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumAdjustmentBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0) FROM balance_adjustment_records
        WHERE delta > 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumPositiveManualAdjustmentBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(-delta), 0) FROM balance_adjustment_records
        WHERE delta < 0
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumNegativeManualAdjustmentBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COUNT(*) FROM balance_adjustment_records
        WHERE occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """
    )
    suspend fun countBetween(startInclusive: Long, endExclusive: Long): Int

    @Query("DELETE FROM balance_adjustment_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM balance_adjustment_records")
    suspend fun deleteAll()
}
