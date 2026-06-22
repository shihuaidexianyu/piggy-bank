package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.TimeMath

class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
) {
    suspend operator fun invoke(
        name: String,
        initialBalance: Long,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
        iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
        createdAt: Long = TimeMath.floorToMinute(System.currentTimeMillis()),
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "账户名称不能为空" }
        require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
        require(accountRepository.isActiveNameAvailable(normalizedName)) { "已存在同名账户" }

        val accountId = accountRepository.createAccount(
            Account(
                name = normalizedName,
                initialBalance = initialBalance,
                createdAt = createdAt,
                lastUsedAt = createdAt,
                displayOrder = accountRepository.nextDisplayOrder(),
                colorName = normalizeAccountColorName(colorName),
                iconName = normalizeAccountIconName(iconName),
            ),
        )
        accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
        return accountId
    }
}
