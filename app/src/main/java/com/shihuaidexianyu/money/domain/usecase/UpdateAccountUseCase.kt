package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName

class UpdateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val transactionRunner: DatabaseTransactionRunner,
) {
    suspend operator fun invoke(
        accountId: Long,
        name: String,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
        iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
    ) {
        transactionRunner.runInTransaction {
            val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户不存在" }
            account.requireOpenForMutation("修改账户")
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty()) { "账户名称不能为空" }
            require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
            require(accountRepository.isOpenNameAvailable(normalizedName, excludeId = accountId)) { "已存在同名账户" }

            accountRepository.updateAccount(
                account.copy(
                    name = normalizedName,
                    colorName = normalizeAccountColorName(colorName),
                    iconName = normalizeAccountIconName(iconName),
                ),
            )
            // Task 4 will migrate reminder settings into Room so this write can become fully
            // database-atomic with the account row. Keeping it in this block still prevents a
            // concurrent close from interleaving between the open guard and either update.
            accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
        }
    }
}

