package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashFlowRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CashFlowRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(record: CashFlowRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<CashFlowRecordEntity>)

    @Query(
        """
        UPDATE cash_flow_records
        SET accountId = :accountId,
            direction = :direction,
            amount = :amount,
            note = :note,
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
        direction: String,
        amount: Long,
        note: String,
        occurredAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE cash_flow_records
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
        UPDATE cash_flow_records
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

    @Query("SELECT * FROM cash_flow_records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun queryById(id: Long): CashFlowRecordEntity?

    @Query("SELECT * FROM cash_flow_records WHERE id = :id LIMIT 1")
    suspend fun queryStoredById(id: Long): CashFlowRecordEntity?

    @Query("SELECT * FROM cash_flow_records WHERE operationId = :operationId LIMIT 1")
    suspend fun queryByOperationId(operationId: String): CashFlowRecordEntity?

    @Query("SELECT COUNT(*) FROM cash_flow_records WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM cash_flow_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<CashFlowRecordEntity>

    @Query("SELECT * FROM cash_flow_records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE accountId = :accountId AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT note FROM cash_flow_records
        WHERE direction = :direction
            AND (:accountId IS NULL OR accountId = :accountId)
            AND deletedAt IS NULL
            AND TRIM(note) != ''
        GROUP BY note
        ORDER BY MAX(occurredAt) DESC, MAX(id) DESC
        LIMIT :limit
        """,
    )
    suspend fun queryRecentNotes(direction: String, accountId: Long?, limit: Int): List<String>

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE direction = :direction
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryActiveByDirectionBetween(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'inflow'
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumInflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE accountId = :accountId
            AND direction = 'outflow'
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumOutflowBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE direction = 'inflow'
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumCashInflowBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM cash_flow_records
        WHERE direction = 'outflow'
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumCashOutflowBetween(startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COUNT(*) FROM cash_flow_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun countActiveBetween(startInclusive: Long, endExclusive: Long): Int

    @Query(
        """
        SELECT * FROM cash_flow_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC
        """,
    )
    suspend fun queryActiveBetween(startInclusive: Long, endExclusive: Long): List<CashFlowRecordEntity>

    @Query(
        """
        SELECT
            CASE WHEN TRIM(note) = '' THEN '未填写用途' ELSE note END AS purpose,
            COALESCE(SUM(amount), 0) AS amount
        FROM cash_flow_records
        WHERE direction = :direction
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        GROUP BY CASE WHEN TRIM(note) = '' THEN '未填写用途' ELSE note END
        ORDER BY amount DESC
        """,
    )
    suspend fun queryPurposeTotals(
        direction: String,
        startInclusive: Long,
        endExclusive: Long,
    ): List<PurposeTotalRow>

    @Query(
        """
        SELECT
            CAST(((occurredAt / 1000) + :zoneOffsetSeconds) / 86400 AS INTEGER) AS epochDay,
            direction AS direction,
            COALESCE(SUM(amount), 0) AS amount
        FROM cash_flow_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        GROUP BY epochDay, direction
        ORDER BY epochDay ASC
        """,
    )
    suspend fun queryDailyTotals(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetSeconds: Int,
    ): List<CashFlowDailyTotalRow>

    @Query("DELETE FROM cash_flow_records")
    suspend fun deleteAll()
}
