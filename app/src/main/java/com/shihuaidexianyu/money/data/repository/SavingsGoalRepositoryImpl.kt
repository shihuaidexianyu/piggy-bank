package com.shihuaidexianyu.money.data.repository

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.SavingsGoalDao
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.SavingsGoalAccountLinkEntity
import com.shihuaidexianyu.money.data.entity.SavingsGoalEntity
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SavingsGoalRepositoryImpl(
    private val database: MoneyDatabase,
    private val savingsGoalDao: SavingsGoalDao,
) : SavingsGoalRepository {

    override fun observeAll(): Flow<List<SavingsGoal>> = combine(
        savingsGoalDao.observeAll(),
        savingsGoalDao.observeAllLinks(),
    ) { goals, links ->
        val linksByGoal = links.groupBy { it.goalId }.mapValues { entry -> entry.value.map { it.accountId } }
        goals.map { it.toDomain(linksByGoal[it.id] ?: emptyList()) }
    }

    override suspend fun queryAll(): List<SavingsGoal> {
        val goals = savingsGoalDao.queryAll()
        val links = savingsGoalDao.queryAllLinks()
        val linksByGoal = links.groupBy { it.goalId }.mapValues { entry -> entry.value.map { it.accountId } }
        return goals.map { it.toDomain(linksByGoal[it.id] ?: emptyList()) }
    }

    override suspend fun getGoalById(id: Long): SavingsGoal? {
        val goal = savingsGoalDao.queryById(id) ?: return null
        val allLinks = savingsGoalDao.queryAllLinks()
        val accountIds = allLinks.filter { it.goalId == id }.map { it.accountId }
        return goal.toDomain(accountIds)
    }

    override suspend fun createGoal(
        name: String,
        targetAmount: Long,
        accountIds: List<Long>,
        createdAt: Long,
    ): Long {
        return database.withTransaction {
            val goalId = savingsGoalDao.insert(
                SavingsGoalEntity(
                    name = name,
                    targetAmount = targetAmount,
                    createdAt = createdAt,
                ),
            )
            if (accountIds.isNotEmpty()) {
                savingsGoalDao.insertLinks(accountIds.map { SavingsGoalAccountLinkEntity(goalId = goalId, accountId = it) })
            }
            goalId
        }
    }

    override suspend fun updateGoal(goal: SavingsGoal) {
        database.withTransaction {
            savingsGoalDao.update(goal.toEntity())
            savingsGoalDao.deleteLinksForGoal(goal.id)
            if (goal.accountIds.isNotEmpty()) {
                savingsGoalDao.insertLinks(goal.accountIds.map { SavingsGoalAccountLinkEntity(goalId = goal.id, accountId = it) })
            }
        }
    }

    override suspend fun deleteGoal(id: Long) {
        database.withTransaction {
            savingsGoalDao.delete(id)
        }
    }
}

internal fun SavingsGoalEntity.toDomain(accountIds: List<Long>): SavingsGoal = SavingsGoal(
    id = id,
    name = name,
    targetAmount = targetAmount,
    createdAt = createdAt,
    accountIds = accountIds,
)

fun SavingsGoal.toEntity(): SavingsGoalEntity = SavingsGoalEntity(
    id = id,
    name = name,
    targetAmount = targetAmount,
    createdAt = createdAt,
)
