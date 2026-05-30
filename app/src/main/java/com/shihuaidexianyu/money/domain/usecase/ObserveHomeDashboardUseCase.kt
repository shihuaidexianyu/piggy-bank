package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.util.AccountStatusUtils
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class HomeDashboardSnapshot(
    val settings: AppSettings,
    val totalAssets: Long,
    val periodAssetChange: Long,
    val periodNetInflow: Long,
    val periodNetOutflow: Long,
    val staleAccountCount: Int,
    val activeAccounts: List<Account>,
    val staleAccounts: List<Account>,
    val accountBalances: Map<Long, Long>,
    val dueReminders: List<RecurringReminder>,
)

class ObserveHomeDashboardUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeDashboardSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            accountReminderSettingsRepository.observeReminderConfigs(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
            recurringReminderRepository.observeDueReminders(),
        ) { accounts, reminderConfigs, settings, _, dueReminders ->
            Triple(accounts, reminderConfigs, settings) to dueReminders
        }.mapLatest { (triple, dueReminders) ->
            val (accounts, reminderConfigs, settings) = triple
            buildSnapshot(accounts, reminderConfigs, settings, dueReminders)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
        dueReminders: List<RecurringReminder>,
    ): HomeDashboardSnapshot = coroutineScope {
        val range = TimeRangeUtils.currentRange(settings.homePeriod)
        val periodStartBaselineAt = (range.startAtMillis - 1L).coerceAtLeast(0L)
        val balanceJobs = accounts.map { account ->
            async { account.id to calculateCurrentBalanceUseCase(account.id) }
        }
        val openingBalanceJobs = accounts
            .filter { it.createdAt <= periodStartBaselineAt }
            .map { account ->
                async { calculateCurrentBalanceUseCase(account.id, periodStartBaselineAt) }
            }
        val inflowJob = async { transactionRepository.sumAllInflowBetween(range.startAtMillis, range.endAtMillis) }
        val outflowJob = async { transactionRepository.sumAllOutflowBetween(range.startAtMillis, range.endAtMillis) }

        val balances = balanceJobs.associate { it.await() }
        val totalAssets = balances.values.sum()
        val openingTotalAssets = openingBalanceJobs.sumOf { it.await() }
        val staleAccounts = accounts.filter { account ->
            AccountStatusUtils.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
            )
        }
        HomeDashboardSnapshot(
            settings = settings,
            totalAssets = totalAssets,
            periodAssetChange = totalAssets - openingTotalAssets,
            periodNetInflow = inflowJob.await(),
            periodNetOutflow = outflowJob.await(),
            staleAccountCount = staleAccounts.size,
            activeAccounts = accounts,
            staleAccounts = staleAccounts,
            accountBalances = balances,
            dueReminders = dueReminders,
        )
    }
}
