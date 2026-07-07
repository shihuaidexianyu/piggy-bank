package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shihuaidexianyu.money.data.entity.SavingsGoalAccountLinkEntity
import com.shihuaidexianyu.money.data.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGoals(goals: List<SavingsGoalEntity>)

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC, id ASC")
    suspend fun queryAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): SavingsGoalEntity?

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<SavingsGoalAccountLinkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLinks(links: List<SavingsGoalAccountLinkEntity>)

    @Query("DELETE FROM savings_goal_account_links WHERE goalId = :goalId")
    suspend fun deleteLinksForGoal(goalId: Long)

    @Query("SELECT * FROM savings_goal_account_links")
    suspend fun queryAllLinks(): List<SavingsGoalAccountLinkEntity>

    @Query("SELECT * FROM savings_goal_account_links")
    fun observeAllLinks(): Flow<List<SavingsGoalAccountLinkEntity>>

    @Query("DELETE FROM savings_goal_account_links")
    suspend fun deleteAllLinks()
}
