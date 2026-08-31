package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.AiMutationJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMutationJournalDao {
    @Query("SELECT * FROM ai_mutation_journal ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiMutationJournalEntity>>

    @Query("SELECT COUNT(*) FROM ai_mutation_journal WHERE status = 'applied'")
    fun observeAppliedCount(): Flow<Int>

    @Query("SELECT * FROM ai_mutation_journal WHERE status = 'applied' ORDER BY id DESC LIMIT 1")
    fun observeLatestApplied(): Flow<AiMutationJournalEntity?>

    @Query("SELECT * FROM ai_mutation_journal ORDER BY id DESC LIMIT :limit")
    suspend fun queryRecent(limit: Int): List<AiMutationJournalEntity>

    @Query("SELECT * FROM ai_mutation_journal WHERE requestId = :requestId LIMIT 1")
    suspend fun queryByRequestId(requestId: String): AiMutationJournalEntity?

    @Query("SELECT * FROM ai_mutation_journal WHERE undoRequestId = :requestId LIMIT 1")
    suspend fun queryByUndoRequestId(requestId: String): AiMutationJournalEntity?

    @Query("SELECT * FROM ai_mutation_journal WHERE status = 'applied' ORDER BY id DESC LIMIT 1")
    suspend fun queryLatestApplied(): AiMutationJournalEntity?

    @Insert
    suspend fun insert(entry: AiMutationJournalEntity): Long

    @Query(
        """
        UPDATE ai_mutation_journal
        SET status = 'undone', resolvedAt = :undoneAt, undoRequestId = :undoRequestId
        WHERE id = :id AND status = 'applied'
        """,
    )
    suspend fun markUndone(id: Long, undoneAt: Long, undoRequestId: String): Int

    @Query(
        """
        UPDATE ai_mutation_journal
        SET status = 'discarded', resolvedAt = :discardedAt
        WHERE id = :id
          AND status = 'applied'
          AND id = (SELECT id FROM ai_mutation_journal WHERE status = 'applied' ORDER BY id DESC LIMIT 1)
        """,
    )
    suspend fun discardLatest(id: Long, discardedAt: Long): Int

    @Query("DELETE FROM ai_mutation_journal")
    suspend fun deleteAll()
}
