package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiMutationJournalItem
import kotlinx.coroutines.flow.Flow

interface AiMutationJournalRepository {
    fun observeRecent(limit: Int): Flow<List<AiMutationJournalEntry>>
    fun observeAppliedCount(): Flow<Int>
    fun observeLatestApplied(): Flow<AiMutationJournalEntry?>
    suspend fun queryRecent(limit: Int): List<AiMutationJournalEntry>
    suspend fun queryByRequestId(requestId: String): AiMutationJournalEntry?
    suspend fun queryByUndoRequestId(requestId: String): AiMutationJournalEntry?
    suspend fun queryLatestApplied(): AiMutationJournalEntry?
    suspend fun insert(entry: AiMutationJournalEntry): Long

    /** Inserts one batch entry plus its items atomically (caller's transaction). */
    suspend fun insertBatch(entry: AiMutationJournalEntry, items: List<AiMutationJournalItem>): Long

    /** Items of one batch entry in patch order (itemIndex ascending). */
    suspend fun queryItems(journalId: Long): List<AiMutationJournalItem>

    /** Marks every applied item of a batch undone (caller's transaction). */
    suspend fun markItemsUndone(journalId: Long)

    suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Boolean
    suspend fun discardLatest(id: Long, discardedAt: Long): Boolean
    suspend fun deleteAll()
}
