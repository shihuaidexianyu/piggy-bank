package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH

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
        require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
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

