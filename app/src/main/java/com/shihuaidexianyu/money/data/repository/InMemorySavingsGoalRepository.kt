package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.SAVINGS_GOAL_ID
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.time.nextMutationTimestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySavingsGoalRepository : SavingsGoalRepository {
    private val mutex = Mutex()
    private val goal = MutableStateFlow<SavingsGoal?>(null)

    override fun observe(): Flow<SavingsGoal?> = goal.asStateFlow()

    override suspend fun query(): SavingsGoal? = mutex.withLock { goal.value }

    override suspend fun upsert(targetAmount: Long, now: Long) {
        require(targetAmount > 0L) { "储蓄目标金额必须大于零" }
        mutex.withLock {
            val existing = goal.value
            goal.value = when {
                existing == null -> SavingsGoal(
                    id = SAVINGS_GOAL_ID,
                    targetAmount = targetAmount,
                    createdAt = now,
                    updatedAt = now,
                )
                existing.targetAmount == targetAmount -> existing
                else -> existing.copy(
                    targetAmount = targetAmount,
                    updatedAt = nextMutationTimestamp(now, existing.updatedAt),
                )
            }
        }
    }

    override suspend fun clear() {
        mutex.withLock { goal.value = null }
    }
}
