package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface SavingsGoalRepository {
    fun observeAll(): Flow<List<SavingsGoal>>
    suspend fun queryAll(): List<SavingsGoal>
    suspend fun getGoalById(id: Long): SavingsGoal?
    suspend fun createGoal(targetAmount: Long, createdAt: Long): Long
    suspend fun updateGoal(goal: SavingsGoal)
    suspend fun deleteGoal(id: Long)
}
