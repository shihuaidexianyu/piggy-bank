package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.SyncChangeLogEntity
import com.shihuaidexianyu.money.data.entity.SyncDatasetEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity

/** Max change-log revision for one snapshot row (absent rows are filtered out by the query). */
data class SyncLatestRevisionRow(
    val recordId: Long,
    val revision: Long,
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_dataset WHERE singletonId = 1")
    suspend fun queryDataset(): SyncDatasetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDataset(dataset: SyncDatasetEntity)

    @Insert
    suspend fun insertChange(change: SyncChangeLogEntity)

    @Query(
        """
        SELECT MAX(revision) FROM sync_change_log
        WHERE entityKind = :entityKind AND recordId = :recordId
        """,
    )
    suspend fun queryLatestRevisionFor(entityKind: String, recordId: Long): Long?

    @Query(
        """
        SELECT * FROM sync_change_log
        WHERE revision > :afterRevision
        ORDER BY revision ASC
        LIMIT :limit
        """,
    )
    suspend fun queryChangesAfter(afterRevision: Long, limit: Int): List<SyncChangeLogEntity>

    @Query("SELECT MIN(revision) FROM sync_change_log")
    suspend fun minLoggedRevision(): Long?

    @Query("DELETE FROM sync_change_log")
    suspend fun clearChangeLog()

    @Query(
        """
        SELECT recordId, MAX(revision) AS revision FROM sync_change_log
        WHERE entityKind = :entityKind AND recordId IN (:recordIds)
        GROUP BY recordId
        """,
    )
    suspend fun queryLatestRevisions(entityKind: String, recordIds: List<Long>): List<SyncLatestRevisionRow>

    // Keyset snapshot pages over the current projection of each mirrored table (soft-deleted
    // rows included; callers surface them as tombstones).

    @Query("SELECT * FROM accounts WHERE id > :afterRecordId ORDER BY id ASC LIMIT :limit")
    suspend fun queryAccountSnapshotRows(afterRecordId: Long, limit: Int): List<AccountEntity>

    @Query("SELECT * FROM cash_flow_records WHERE id > :afterRecordId ORDER BY id ASC LIMIT :limit")
    suspend fun queryCashFlowSnapshotRows(afterRecordId: Long, limit: Int): List<CashFlowRecordEntity>

    @Query("SELECT * FROM transfer_records WHERE id > :afterRecordId ORDER BY id ASC LIMIT :limit")
    suspend fun queryTransferSnapshotRows(afterRecordId: Long, limit: Int): List<TransferRecordEntity>

    @Query("SELECT * FROM balance_update_records WHERE id > :afterRecordId ORDER BY id ASC LIMIT :limit")
    suspend fun queryBalanceUpdateSnapshotRows(afterRecordId: Long, limit: Int): List<BalanceUpdateRecordEntity>

    @Query("SELECT * FROM balance_adjustment_records WHERE id > :afterRecordId ORDER BY id ASC LIMIT :limit")
    suspend fun queryBalanceAdjustmentSnapshotRows(
        afterRecordId: Long,
        limit: Int,
    ): List<BalanceAdjustmentRecordEntity>
}
