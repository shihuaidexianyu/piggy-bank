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

    @Query("DELETE FROM balance_adjustment_records WHERE sourceUpdateRecordId = :sourceUpdateRecordId")
    suspend fun deleteBySourceUpdateRecordId(sourceUpdateRecordId: Long)

    @Query("SELECT * FROM balance_adjustment_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE sourceUpdateRecordId = 0
            AND occurredAt BETWEEN :startAt AND :endAt
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryManualBetween(startAt: Long, endAt: Long): List<BalanceAdjustmentRecordEntity>

    @Query("SELECT COUNT(*) FROM balance_adjustment_records")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM balance_adjustment_records
        WHERE accountId = :accountId
            AND sourceUpdateRecordId = 0
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<BalanceAdjustmentRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(delta), 0) FROM balance_adjustment_records
        WHERE accountId = :accountId
            AND sourceUpdateRecordId = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumAdjustmentBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query("DELETE FROM balance_adjustment_records")
    suspend fun deleteAll()
}
