package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface SavingsGoalRepository {
    fun observe(): Flow<SavingsGoal?>
    suspend fun query(): SavingsGoal?
    suspend fun upsert(targetAmount: Long, now: Long)
    suspend fun clear()
}
