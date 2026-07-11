package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.SavingsGoalEntity
import com.shihuaidexianyu.money.domain.model.SAVINGS_GOAL_ID
import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE id = 1 LIMIT 1")
    suspend fun query(): SavingsGoalEntity?

    @Transaction
    suspend fun upsert(targetAmount: Long, now: Long) {
        require(targetAmount > 0L) { "净资产目标金额必须大于零" }
        val existing = query()
        if (existing == null) {
            insert(
                SavingsGoalEntity(
                    id = SAVINGS_GOAL_ID,
                    targetAmount = targetAmount,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else if (existing.targetAmount != targetAmount) {
            update(
                existing.copy(
                    targetAmount = targetAmount,
                    updatedAt = nextMutationTimestamp(now, existing.updatedAt),
                ),
            )
        }
    }

    @Query("DELETE FROM savings_goals")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}
