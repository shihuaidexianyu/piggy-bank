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

    override fun observeAll(): Flow<List<SavingsGoal>> =
        savingsGoalDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun queryAll(): List<SavingsGoal> =
        savingsGoalDao.queryAll().map { it.toDomain() }

    override suspend fun getGoalById(id: Long): SavingsGoal? =
        savingsGoalDao.queryById(id)?.toDomain()

    override suspend fun createGoal(targetAmount: Long, createdAt: Long): Long {
        return savingsGoalDao.insert(
            SavingsGoalEntity(
                targetAmount = targetAmount,
                createdAt = createdAt,
            ),
        )
    }

    override suspend fun updateGoal(goal: SavingsGoal) {
        savingsGoalDao.update(goal.toEntity())
    }

    override suspend fun deleteGoal(id: Long) {
        savingsGoalDao.delete(id)
    }
}

internal fun SavingsGoalEntity.toDomain(): SavingsGoal = SavingsGoal(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
)

fun SavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    targetAmount = targetAmount,
    createdAt = createdAt,
)
