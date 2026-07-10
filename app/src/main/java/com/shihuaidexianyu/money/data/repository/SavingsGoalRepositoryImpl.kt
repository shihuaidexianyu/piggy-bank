package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.SavingsGoalDao
import com.shihuaidexianyu.money.data.entity.SavingsGoalEntity
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavingsGoalRepositoryImpl(
    private val savingsGoalDao: SavingsGoalDao,
) : SavingsGoalRepository {
    override fun observe(): Flow<SavingsGoal?> = savingsGoalDao.observe().map { it?.toDomain() }

    override suspend fun query(): SavingsGoal? = savingsGoalDao.query()?.toDomain()

    override suspend fun upsert(targetAmount: Long, now: Long) {
        savingsGoalDao.upsert(targetAmount, now)
    }

    override suspend fun clear() {
        savingsGoalDao.clear()
    }
}

internal fun SavingsGoalEntity.toDomain(): SavingsGoal = SavingsGoal(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
