package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryAccountReminderSettingsRepository : AccountReminderSettingsRepository {
    private val reminderConfigs = MutableStateFlow<Map<Long, BalanceUpdateReminderConfig>>(emptyMap())
    private val mutex = Mutex()

    override fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>> = reminderConfigs

    override suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig =
        reminderConfigs.value[accountId] ?: BalanceUpdateReminderConfig()

    override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
        mutex.withLock {
            val previousBoundary = reminderConfigs.value[accountId]?.lastNotifiedBoundaryAt
            reminderConfigs.value = reminderConfigs.value + (
                accountId to config.copy(lastNotifiedBoundaryAt = previousBoundary)
            )
        }
    }

    override suspend fun setEnabled(accountId: Long, enabled: Boolean) {
        mutex.withLock {
            val current = reminderConfigs.value[accountId] ?: BalanceUpdateReminderConfig()
            reminderConfigs.value = reminderConfigs.value + (accountId to current.copy(isEnabled = enabled))
        }
    }

    override suspend fun compareAndSetLastNotifiedBoundary(
        accountId: Long,
        expected: Long?,
        newValue: Long,
    ): Boolean = mutex.withLock {
        val current = reminderConfigs.value[accountId] ?: return@withLock false
        if (!current.isEnabled || current.lastNotifiedBoundaryAt != expected) return@withLock false
        reminderConfigs.value = reminderConfigs.value + (
            accountId to current.copy(lastNotifiedBoundaryAt = newValue)
        )
        true
    }

    override suspend fun resetLastNotifiedBoundary(accountId: Long) {
        mutex.withLock {
            val current = reminderConfigs.value[accountId] ?: return@withLock
            reminderConfigs.value = reminderConfigs.value + (
                accountId to current.copy(lastNotifiedBoundaryAt = null)
            )
        }
    }

    override suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        mutex.withLock {
            reminderConfigs.value = configs.mapValues { (_, config) ->
                config.copy(lastNotifiedBoundaryAt = null)
            }
        }
    }
}
