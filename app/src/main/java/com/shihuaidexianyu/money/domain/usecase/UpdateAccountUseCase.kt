package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig

class UpdateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) {
    suspend operator fun invoke(
        accountId: Long,
        name: String,
        groupType: AccountGroupType,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
    ) {
        val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "账户名称不能为空" }
        require(accountRepository.isActiveNameAvailable(normalizedName, excludeId = accountId)) { "已存在同名账户" }

        accountRepository.updateAccount(
            account.copy(
                name = normalizedName,
                groupType = groupType.value,
            ),
        )
        accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
    }
}

