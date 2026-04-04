package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.InvestmentSettlementEntity
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.AccountStatusUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

data class AccountDetailSnapshot(
    val account: AccountEntity?,
    val settings: AppSettings,
    val reminderConfig: BalanceUpdateReminderConfig,
    val currentBalance: Long,
    val isStale: Boolean,
    val latestSettlement: InvestmentSettlementSummary?,
)

class ObserveAccountDetailUseCase(
    private val accountId: Long,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AccountDetailSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            accountRepository.observeArchivedAccounts(),
            accountReminderSettingsRepository.observeReminderConfigs(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
        ) { active, archived, reminderConfigs, settings, _ ->
            Quadruple(active, archived, reminderConfigs, settings)
        }.mapLatest { (active, archived, reminderConfigs, settings) ->
            buildSnapshot(
                account = (active + archived).firstOrNull { it.id == accountId },
                settings = settings,
                reminderConfigs = reminderConfigs,
            )
        }
    }

    private suspend fun buildSnapshot(
        account: AccountEntity?,
        settings: AppSettings,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): AccountDetailSnapshot {
        if (account == null) {
            return AccountDetailSnapshot(
                account = null,
                settings = settings,
                reminderConfig = BalanceUpdateReminderConfig(),
                currentBalance = 0L,
                isStale = false,
                latestSettlement = null,
            )
        }

        val reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig()
        val settlements = if (account.groupType == AccountGroupType.INVESTMENT.value) {
            transactionRepository.queryInvestmentSettlementsByAccountId(account.id)
        } else {
            emptyList()
        }
        val latestSettlement = settlements.maxWithOrNull(
            compareBy<InvestmentSettlementEntity> { it.periodEndAt }.thenBy { it.id },
        )?.let { settlement ->
            InvestmentSettlementSummary(
                previousBalance = settlement.previousBalance,
                currentBalance = settlement.currentBalance,
                netTransferIn = settlement.netTransferIn,
                netTransferOut = settlement.netTransferOut,
                pnl = settlement.pnl,
                returnRate = settlement.returnRate,
                periodStartAt = settlement.periodStartAt,
                periodEndAt = settlement.periodEndAt,
            )
        }

        return AccountDetailSnapshot(
            account = account,
            settings = settings,
            reminderConfig = reminderConfig,
            currentBalance = calculateCurrentBalanceUseCase(account.id),
            isStale = AccountStatusUtils.isStale(account, reminderConfig = reminderConfig),
            latestSettlement = latestSettlement,
        )
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

