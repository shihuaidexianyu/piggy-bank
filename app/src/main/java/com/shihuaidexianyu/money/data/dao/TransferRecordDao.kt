package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: TransferRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<TransferRecordEntity>)

    @Update
    suspend fun update(record: TransferRecordEntity)

    @Query("UPDATE transfer_records SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("SELECT * FROM transfer_records WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun queryById(id: Long): TransferRecordEntity?

    @Query("SELECT COUNT(*) FROM transfer_records WHERE isDeleted = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM transfer_records ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAll(): List<TransferRecordEntity>

    @Query("SELECT * FROM transfer_records WHERE isDeleted = 0 ORDER BY occurredAt DESC, id DESC")
    suspend fun queryAllActive(): List<TransferRecordEntity>

    @Query(
        """
        SELECT * FROM transfer_records
        WHERE isDeleted = 0
            AND occurredAt BETWEEN :startAt AND :endAt
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun queryActiveBetween(startAt: Long, endAt: Long): List<TransferRecordEntity>

    @Query(
        """
        SELECT * FROM transfer_records
        WHERE (fromAccountId = :accountId OR toAccountId = :accountId)
            AND isDeleted = 0
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    suspend fun queryByAccountId(accountId: Long): List<TransferRecordEntity>

    @Query(
        """
        SELECT note FROM transfer_records
        WHERE (:fromAccountId IS NULL OR fromAccountId = :fromAccountId)
            AND (:toAccountId IS NULL OR toAccountId = :toAccountId)
            AND isDeleted = 0
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
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumTransferInBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transfer_records
        WHERE fromAccountId = :accountId
            AND isDeleted = 0
            AND occurredAt > :startAt
            AND occurredAt <= :endAt
        """,
    )
    suspend fun sumTransferOutBetween(accountId: Long, startAt: Long, endAt: Long): Long

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}
