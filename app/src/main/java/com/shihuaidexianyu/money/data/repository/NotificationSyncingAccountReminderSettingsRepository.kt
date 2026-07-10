package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository

class NotificationSyncingAccountReminderSettingsRepository(
    private val delegate: AccountReminderSettingsRepository,
    private val requester: NotificationSyncRequester,
) : AccountReminderSettingsRepository by delegate {
    override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
        delegate.updateReminderConfig(accountId, config)
        request()
    }

    override suspend fun setEnabled(accountId: Long, enabled: Boolean) {
        delegate.setEnabled(accountId, enabled)
        request()
    }

    override suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        delegate.replaceReminderConfigs(configs)
        request()
    }

    private fun request() {
        runCatching { requester.request(NotificationSyncReason.CONFIG_CHANGED) }
    }
}
