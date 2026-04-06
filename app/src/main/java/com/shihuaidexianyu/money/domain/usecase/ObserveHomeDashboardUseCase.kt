package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

data class HomeDashboardSnapshot(
    val settings: AppSettings,
    val totalAssets: Long,
    val periodNetInflow: Long,
    val periodNetOutflow: Long,
    val staleAccountCount: Int,
    val activeAccounts: List<AccountEntity>,
    val dueReminders: List<RecurringReminderEntity>,
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
        }
    }

    private suspend fun buildSnapshot(
        accounts: List<AccountEntity>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
        dueReminders: List<RecurringReminderEntity>,
    ): HomeDashboardSnapshot = coroutineScope {
        val range = TimeRangeUtils.currentRange(settings.homePeriod)
        val balanceJobs = accounts.map { account ->
            async { account.id to calculateCurrentBalanceUseCase(account.id) }
        }
        val inflowJob = async { transactionRepository.sumAllInflowBetween(range.startAtMillis, range.endAtMillis) }
        val outflowJob = async { transactionRepository.sumAllOutflowBetween(range.startAtMillis, range.endAtMillis) }

        val balances = balanceJobs.associate { it.await() }
        HomeDashboardSnapshot(
            settings = settings,
            totalAssets = balances.values.sum(),
            periodNetInflow = inflowJob.await(),
            periodNetOutflow = outflowJob.await(),
            staleAccountCount = accounts.count { account ->
                AccountStatusUtils.isStale(
                    account,
                    reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
                )
            },
            activeAccounts = accounts,
            dueReminders = dueReminders,
        )
    }
}

