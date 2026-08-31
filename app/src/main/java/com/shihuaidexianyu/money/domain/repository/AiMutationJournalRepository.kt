package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
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
    suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Boolean
    suspend fun discardLatest(id: Long, discardedAt: Long): Boolean
    suspend fun deleteAll()
}
