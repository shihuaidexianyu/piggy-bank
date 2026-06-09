package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryAccountReminderSettingsRepository : AccountReminderSettingsRepository {
    private val reminderConfigs = MutableStateFlow<Map<Long, BalanceUpdateReminderConfig>>(emptyMap())

    override fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>> = reminderConfigs

    override suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig {
        return reminderConfigs.value[accountId] ?: BalanceUpdateReminderConfig()
    }

    override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
        reminderConfigs.value = if (config == BalanceUpdateReminderConfig()) {
            reminderConfigs.value - accountId
        } else {
            reminderConfigs.value + (accountId to config)
        }
    }

    override suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        reminderConfigs.value = configs.filterValues { it != BalanceUpdateReminderConfig() }
    }
}

