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
    private val accountLifecycleCoordinator: AccountLifecycleCoordinator,
) {
    suspend operator fun invoke(
        accountId: Long,
        name: String,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
        iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
    ) {
        accountLifecycleCoordinator.withLifecycleLock {
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
            }
            // This DataStore write is intentionally outside the Room transaction. The shared
            // lifecycle lock prevents an in-process close from entering between the two writes,
            // but Room + DataStore cannot be crash-atomic until Task 4 moves configs into Room.
            accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
        }
    }
}

