package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_COLOR_NAME
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName
import com.shihuaidexianyu.money.domain.model.TimeMath
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase

class CreateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val clockProvider: ClockProvider,
    private val transactionRunner: DatabaseTransactionRunner,
    private val appendSyncChangesUseCase: AppendSyncChangesUseCase? = null,
) {
    suspend operator fun invoke(
        name: String,
        initialBalance: Long,
        balanceUpdateReminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
        iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
        kind: AccountKind = AccountKind.DEFAULT,
        createdAt: Long = TimeMath.floorToMinute(clockProvider.nowMillis()),
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { ValidationErrorText.ACCOUNT_NAME_REQUIRED }
        require(normalizedName.length <= MAX_ACCOUNT_NAME_LENGTH) { "账户名称不能超过 ${MAX_ACCOUNT_NAME_LENGTH} 个字符" }
        require(accountRepository.isOpenNameAvailable(normalizedName)) { ValidationErrorText.DUPLICATE_ACCOUNT_NAME }

        // Account insert and reminder-config write share one transaction so the account can
        // never exist without its config, and the sync change-log entry commits with them.
        return transactionRunner.runInTransaction {
            val account = Account(
                name = normalizedName,
                initialBalance = initialBalance,
                createdAt = createdAt,
                lastUsedAt = createdAt,
                displayOrder = accountRepository.nextDisplayOrder(),
                colorName = normalizeAccountColorName(colorName),
                iconName = normalizeAccountIconName(iconName),
                kind = kind,
            )
            val accountId = accountRepository.createAccount(account)
            accountReminderSettingsRepository.updateReminderConfig(accountId, balanceUpdateReminderConfig)
            appendSyncChangesUseCase?.upsertAccount(account.copy(id = accountId))
            accountId
        }
    }
}
