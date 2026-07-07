package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemorySavingsGoalRepository : SavingsGoalRepository {
    private val goals = MutableStateFlow<Map<Long, SavingsGoal>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<SavingsGoal>> = goals.map { it.values.sortedBy { g -> g.id } }

    override suspend fun queryAll(): List<SavingsGoal> = goals.value.values.sortedBy { it.id }

    override suspend fun getGoalById(id: Long): SavingsGoal? = goals.value[id]

    override suspend fun createGoal(targetAmount: Long, createdAt: Long): Long {
        val id = nextId++
        goals.value = goals.value + (id to SavingsGoal(
            id = id,
            targetAmount = targetAmount,
            createdAt = createdAt,
        ))
        return id
    }

    override suspend fun updateGoal(goal: SavingsGoal) {
        goals.value = goals.value + (goal.id to goal)
    }

    override suspend fun deleteGoal(id: Long) {
        goals.value = goals.value - id
    }
}
