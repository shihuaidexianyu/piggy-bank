package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_mutation_journal",
    indices = [
        Index(value = ["requestId"], unique = true),
        Index(value = ["undoRequestId"], unique = true),
        Index(value = ["status", "id"]),
        Index(value = ["recordKind", "recordId", "id"]),
    ],
)
data class AiMutationJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String,
    val sessionId: String,
    val clientName: String,
    val action: String,
    /** Null for batch entries; batches address their records through ai_mutation_journal_items. */
    val recordKind: String?,
    val recordId: Long?,
    val summary: String,
    val beforeSnapshotJson: String?,
    /** Null for batch entries; per-item snapshots live in ai_mutation_journal_items. */
    val afterSnapshotJson: String?,
    val undoTokenJson: String?,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val undoRequestId: String?,
    /** "single" or "batch"; SQL DEFAULT 'single' is applied by the 19 -> 20 migration. */
    val entryType: String,
    val itemCount: Int?,
    val appliedCount: Int?,
    val conflictCount: Int?,
)
