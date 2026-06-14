package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CashFlowRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<CashFlowRecordEntity>)

    @Update
    suspend fun update(record: CashFlowRecordEntity)

    @Query("UPDATE cash_flow_records SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("SELECT * FROM cash_flow_records WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun queryById(id: Long): CashFlowRecordEntity?

    @Query("SELECT COUNT(*) FROM cash_flow_records WHERE isDeleted = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM cash_flow_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<CashFlowRecordEntity>

    @Query("SELECT * FROM cash_flow_records WHERE isDeleted = 0 ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE accountId = :accountId AND isDeleted = 0
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT purpose FROM cash_flow_records
        WHERE direction = :direction
            AND (:accountId IS NULL OR accountId = :accountId)
            AND isDeleted = 0
            AND TRIM(purpose) != ''
        GROUP BY purpose
        ORDER BY MAX(occurredAt) DESC, MAX(id) DESC
        LIMIT :limit
        """,
    )
    suspend fun queryRecentPurposes(direction: String, accountId: Long?, limit: Int): List<String>

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE direction = :direction
            AND isDeleted = 0
            AND occurredAt >= :startAt
            AND occurredAt <= :endAt
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryActiveByDirectionBetween(direction: String, startAt: Long, endAt: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'inflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumInflowBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'outflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumOutflowBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE direction = 'inflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumCashInflowBetween(startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE direction = 'outflow'
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumCashOutflowBetween(startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COUNT(*) FROM cash_flow_records
        WHERE isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun countActiveBetween(startAt: Long, endAt: Long): Int

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE isDeleted = 0 AND occurredAt >= :startAt AND occurredAt <= :endAt
        ORDER BY occurredAt ASC
        """,
    )
    suspend fun queryActiveBetween(startAt: Long, endAt: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT
            CASE WHEN TRIM(purpose) = '' THEN '未填写用途' ELSE purpose END AS purpose,
            COALESCE(SUM(amount), 0) AS amount
        FROM cash_flow_records
        WHERE direction = :direction
            AND isDeleted = 0
            AND occurredAt >= :startAt
            AND occurredAt <= :endAt
        GROUP BY CASE WHEN TRIM(purpose) = '' THEN '未填写用途' ELSE purpose END
        ORDER BY amount DESC
        """,
    )
    suspend fun queryPurposeTotals(
        direction: String,
        startAt: Long,
        endAt: Long,
    ): List<PurposeTotalRow>

    @Query(
        """
        SELECT
            CAST(((occurredAt / 1000) + :zoneOffsetSeconds) / 86400 AS INTEGER) AS epochDay,
            direction AS direction,
            COALESCE(SUM(amount), 0) AS amount
        FROM cash_flow_records
        WHERE isDeleted = 0
            AND occurredAt >= :startAt
            AND occurredAt <= :endAt
        GROUP BY epochDay, direction
        ORDER BY epochDay ASC
        """,
    )
    suspend fun queryDailyTotals(
        startAt: Long,
        endAt: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotalRow>

    @Query("DELETE FROM cash_flow_records")
    suspend fun deleteAll()
}
