package com.shihuaidexianyu.money.data.repository

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.AccountReminderConfigDao
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountReminderSettingsRepositoryImpl(
    private val database: MoneyDatabase,
    private val dao: AccountReminderConfigDao,
) : AccountReminderSettingsRepository {
    override fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>> =
        dao.observeAll().map { rows -> rows.associate { it.accountId to it.toDomain() } }

    override suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig =
        dao.queryByAccountId(accountId)?.toDomain() ?: BalanceUpdateReminderConfig()

    override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
        database.withTransaction {
            val boundary = dao.queryByAccountId(accountId)?.lastNotifiedBoundaryAt
            dao.upsert(config.copy(lastNotifiedBoundaryAt = boundary).toEntity(accountId))
        }
    }

    override suspend fun setEnabled(accountId: Long, enabled: Boolean) {
        database.withTransaction {
            if (dao.updateEnabled(accountId, enabled) == 0) {
                dao.upsert(BalanceUpdateReminderConfig(isEnabled = enabled).toEntity(accountId))
            }
        }
    }

    override suspend fun compareAndSetLastNotifiedBoundary(
        accountId: Long,
        expected: Long?,
        newValue: Long,
    ): Boolean = dao.compareAndSetLastNotifiedBoundary(accountId, expected, newValue) == 1

    override suspend fun resetLastNotifiedBoundary(accountId: Long) {
        dao.resetLastNotifiedBoundary(accountId)
    }

    override suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        database.withTransaction {
            dao.deleteAll()
            dao.upsertAll(configs.map { (accountId, config) ->
                config.copy(lastNotifiedBoundaryAt = null).toEntity(accountId)
            })
        }
    }
}

private fun AccountReminderConfigEntity.toDomain(): BalanceUpdateReminderConfig =
    BalanceUpdateReminderConfig(
        period = BalanceUpdateReminderPeriod.fromValue(period),
        weekday = BalanceUpdateReminderWeekday.fromValue(weekday),
        monthDay = monthDay.coerceIn(1, 31),
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        isEnabled = isEnabled,
        lastNotifiedBoundaryAt = lastNotifiedBoundaryAt,
    )

private fun BalanceUpdateReminderConfig.toEntity(accountId: Long): AccountReminderConfigEntity =
    AccountReminderConfigEntity(
        accountId = accountId,
        period = period.value,
        weekday = weekday.value,
        monthDay = monthDay,
        hour = hour,
        minute = minute,
        isEnabled = isEnabled,
        lastNotifiedBoundaryAt = lastNotifiedBoundaryAt,
    )
