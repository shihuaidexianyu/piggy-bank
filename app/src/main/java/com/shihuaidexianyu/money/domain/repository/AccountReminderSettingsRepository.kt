package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.Flow

interface AccountReminderSettingsRepository {
    fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>>
    suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig
    suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig)
    suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        configs.forEach { (accountId, config) ->
            updateReminderConfig(accountId, config)
        }
    }
}

