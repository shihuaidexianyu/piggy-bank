package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.AiMutationJournalItemEntity

@Dao
interface AiMutationJournalItemDao {
    @Insert
    suspend fun insertAll(items: List<AiMutationJournalItemEntity>)

    @Query("SELECT * FROM ai_mutation_journal_items WHERE journalId = :journalId ORDER BY itemIndex ASC")
    suspend fun queryItems(journalId: Long): List<AiMutationJournalItemEntity>

    @Query(
        """
        UPDATE ai_mutation_journal_items
        SET undoneAt = :undoneAt
        WHERE journalId = :journalId AND undoneAt IS NULL
        """,
    )
    suspend fun markUndone(journalId: Long, undoneAt: Long): Int

    @Query("DELETE FROM ai_mutation_journal_items")
    suspend fun deleteAll()
}
