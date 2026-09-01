package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sync v1 dataset state (docs/design/money-sync-v1-design.md 5.2): exactly one row
 * (singletonId = 1) holding the dataset id and the next change-log revision to allocate.
 */
@Entity(tableName = "sync_dataset")
data class SyncDatasetEntity(
    @PrimaryKey
    val singletonId: Int = 1,
    val datasetId: String,
    val nextRevision: Long,
    val createdAt: Long,
)

/**
 * Append-only sync change log. Rows are written inside the same Room transaction as the ledger
 * mutation they describe; [payloadJson] carries the mirror payload for upserts and is null for
 * deletes (tombstones carry deletedAt instead).
 */
@Entity(
    tableName = "sync_change_log",
    indices = [
        Index(value = ["entityKind", "recordId"]),
    ],
)
data class SyncChangeLogEntity(
    @PrimaryKey
    val revision: Long,
    val entityKind: String,
    val recordId: Long,
    /** "upsert" or "delete". */
    val operation: String,
    val payloadJson: String?,
    val updatedAt: Long,
    /** Set together with updatedAt on delete tombstones; null otherwise. */
    val deletedAt: Long?,
    val requestId: String?,
    val createdAt: Long,
)

/** One patch inside a batch journal entry (sync.push), keyed by (journalId, itemIndex). */
@Entity(
    tableName = "ai_mutation_journal_items",
    primaryKeys = ["journalId", "itemIndex"],
    foreignKeys = [
        ForeignKey(
            entity = AiMutationJournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["journalId"]),
    ],
)
data class AiMutationJournalItemEntity(
    val journalId: Long,
    val itemIndex: Int,
    val patchId: String,
    /** Raw wire entityKind; may be unparseable for invalid patches. */
    val entityKind: String,
    val recordId: Long,
    /** SyncPatchStatus wire value: applied / conflict / invalid. */
    val status: String,
    val beforeSnapshotJson: String?,
    val afterSnapshotJson: String?,
    val expectedUpdatedAt: Long,
    /** Change-log revision allocated for applied items. */
    val revision: Long?,
    /** Server state reported with conflict results, for byte-exact batch replay. */
    val serverUpdatedAt: Long?,
    val serverPayloadJson: String?,
    val errorCode: String?,
    val errorMessage: String?,
    /** Set when the whole batch entry is undone (items have no individual lifecycle). */
    val undoneAt: Long?,
)
