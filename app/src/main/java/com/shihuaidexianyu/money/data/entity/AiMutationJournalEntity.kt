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
    val recordKind: String,
    val recordId: Long,
    val summary: String,
    val beforeSnapshotJson: String?,
    val afterSnapshotJson: String,
    val undoTokenJson: String?,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val undoRequestId: String?,
)
