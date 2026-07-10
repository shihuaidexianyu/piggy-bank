package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: TransferRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(record: TransferRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<TransferRecordEntity>)

    @Query(
        """
        UPDATE transfer_records
        SET fromAccountId = :fromAccountId,
            toAccountId = :toAccountId,
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
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE transfer_records
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
        UPDATE transfer_records
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

    @Query("SELECT * FROM transfer_records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun queryById(id: Long): TransferRecordEntity?

    @Query("SELECT * FROM transfer_records WHERE id = :id LIMIT 1")
    suspend fun queryStoredById(id: Long): TransferRecordEntity?

    @Query("SELECT * FROM transfer_records WHERE operationId = :operationId LIMIT 1")
    suspend fun queryByOperationId(operationId: String): TransferRecordEntity?

    @Query("SELECT COUNT(*) FROM transfer_records WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM transfer_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<TransferRecordEntity>

    @Query("SELECT * FROM transfer_records WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<TransferRecordEntity>

    @Query(
        """
        SELECT * FROM transfer_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryActiveBetween(startInclusive: Long, endExclusive: Long): List<TransferRecordEntity>

    @Query(
        """
        SELECT * FROM transfer_records
        WHERE (fromAccountId = :accountId OR toAccountId = :accountId)
            AND deletedAt IS NULL
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<TransferRecordEntity>

    @Query(
        """
        SELECT note FROM transfer_records
        WHERE (:fromAccountId IS NULL OR fromAccountId = :fromAccountId)
            AND (:toAccountId IS NULL OR toAccountId = :toAccountId)
            AND deletedAt IS NULL
            AND TRIM(note) != ''
        GROUP BY note
        ORDER BY MAX(occurredAt) DESC, MAX(id) DESC
        LIMIT :limit
        """,
    )
    suspend fun queryRecentNotes(fromAccountId: Long?, toAccountId: Long?, limit: Int): List<String>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transfer_records
        WHERE toAccountId = :accountId
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumTransferInBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transfer_records
        WHERE fromAccountId = :accountId
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun sumTransferOutBetween(accountId: Long, startInclusive: Long, endExclusive: Long): Long

    @Query(
        """
        SELECT COUNT(*) FROM transfer_records
        WHERE deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        """,
    )
    suspend fun countActiveBetween(startInclusive: Long, endExclusive: Long): Int

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}
