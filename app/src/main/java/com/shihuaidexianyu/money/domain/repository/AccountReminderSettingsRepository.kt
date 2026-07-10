package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface AccountReminderSettingsRepository {
    fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>>
    suspend fun queryReminderConfigs(): Map<Long, BalanceUpdateReminderConfig> =
        observeReminderConfigs().first()
    suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig
    suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig)
    suspend fun setEnabled(accountId: Long, enabled: Boolean)
    suspend fun compareAndSetLastNotifiedBoundary(
        accountId: Long,
        expected: Long?,
        newValue: Long,
    ): Boolean
    suspend fun acknowledgeStaleBoundary(
        accountId: Long,
        expectedConfig: BalanceUpdateReminderConfig,
        expectedPreviousBoundaryAt: Long?,
        boundaryAt: Long,
    ): Boolean = compareAndSetLastNotifiedBoundary(
        accountId = accountId,
        expected = expectedPreviousBoundaryAt,
        newValue = boundaryAt,
    )
    suspend fun resetLastNotifiedBoundary(accountId: Long)
    suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        configs.forEach { (accountId, config) ->
            updateReminderConfig(accountId, config)
        }
    }
}

