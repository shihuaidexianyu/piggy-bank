package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class UpdateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val transactionRunner: DatabaseTransactionRunner,
    private val accountLifecycleCoordinator: AccountLifecycleCoordinator,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        accountId: Long,
        name: String,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
        iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
        kind: AccountKind = AccountKind.DEFAULT,
    ) {
        accountLifecycleCoordinator.withLifecycleLock {
            transactionRunner.runInTransaction {
                val account = requireNotNull(accountRepository.getAccountById(accountId)) { "账户${ValidationErrorText.NOT_FOUND_SUFFIX}" }
                account.requireOpenForMutation("修改账户")
                val normalizedName = name.trim()
                require(normalizedName.isNotEmpty()) { ValidationErrorText.ACCOUNT_NAME_REQUIRED }
                require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
                require(accountRepository.isOpenNameAvailable(normalizedName, excludeId = accountId)) { ValidationErrorText.DUPLICATE_ACCOUNT_NAME }

                accountRepository.updateAccount(
                    account.copy(
                        name = normalizedName,
                        colorName = normalizeAccountColorName(colorName),
                        iconName = normalizeAccountIconName(iconName),
                        kind = kind,
                    ),
                )
                accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
                accountRepository.getAccountById(accountId)?.let { updated ->
                    appendSyncChangesUseCase?.upsertAccount(updated)
                }
            }
        }
    }
}

