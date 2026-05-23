package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) {
    suspend operator fun invoke(
        name: String,
        initialBalance: Long,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        createdAt: Long = DateTimeTextFormatter.floorToMinute(System.currentTimeMillis()),
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "账户名称不能为空" }
        require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
        require(accountRepository.isActiveNameAvailable(normalizedName)) { "已存在同名账户" }

        val accountId = accountRepository.createAccount(
            AccountEntity(
                name = normalizedName,
                initialBalance = initialBalance,
                createdAt = createdAt,
                lastUsedAt = createdAt,
                displayOrder = accountRepository.nextDisplayOrder(),
            ),
        )
        accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
        return accountId
    }
}

