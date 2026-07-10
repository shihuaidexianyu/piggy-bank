package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
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
    val periodBreakdown: PeriodAssetBreakdown,
    val periodRecordCount: Int,
    val staleAccountCount: Int,
    val openAccounts: List<Account>,
    val staleAccounts: List<Account>,
    val accountBalances: Map<Long, Long>,
    val dueReminders: List<RecurringReminder>,
)

data class PeriodAssetBreakdown(
    val openingAssets: Long,
    val closingAssets: Long,
    val assetChange: Long,
    val cashInflow: Long,
    val cashOutflow: Long,
    val manualAdjustmentIncrease: Long,
    val manualAdjustmentDecrease: Long,
    val reconciliationIncrease: Long,
    val reconciliationDecrease: Long,
) {
    val cashNet: Long
        get() = ledgerSubtractExact(cashInflow, cashOutflow)

    val manualAdjustmentNet: Long
        get() = ledgerSubtractExact(manualAdjustmentIncrease, manualAdjustmentDecrease)

    val reconciliationNet: Long
        get() = ledgerSubtractExact(reconciliationIncrease, reconciliationDecrease)
}

class ObserveHomeDashboardUseCase(
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<HomeDashboardSnapshot> {
        val accountSources = combine(
            accountRepository.observeAllAccounts(),
            accountRepository.observeOpenAccounts(),
        ) { allAccounts, openAccounts -> allAccounts to openAccounts }
        return combine(
            accountSources,
            accountReminderSettingsRepository.observeReminderConfigs(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
            recurringReminderRepository.observeDueReminders(),
        ) { accounts, reminderConfigs, settings, _, dueReminders ->
            Triple(accounts, reminderConfigs, settings) to dueReminders
        }.mapLatest { (triple, dueReminders) ->
            val (accounts, reminderConfigs, settings) = triple
            buildSnapshot(accounts.first, accounts.second, reminderConfigs, settings, dueReminders)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        allAccounts: List<Account>,
        openAccounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
        dueReminders: List<RecurringReminder>,
    ): HomeDashboardSnapshot = coroutineScope {
        val snapshotTimeMillis = clockProvider.nowMillis()
        val zoneId = zoneIdProvider.zoneId()
        val range = TimeRangeCalculator.currentRange(
            period = settings.homePeriod,
            zoneId = zoneId,
            nowMillis = snapshotTimeMillis,
        )
        val balanceJob = async { calculateAccountBalancesUseCase(allAccounts, snapshotTimeMillis) }
        val openingAccounts = allAccounts.filter {
            LedgerBalanceCalculator.openingAt(it) < range.startInclusive
        }
        val openingBalanceJob = async {
            calculateAccountBalancesUseCase.before(openingAccounts, range.startInclusive)
        }
        val newAccountOpeningAssets = allAccounts
            .filter { account -> LedgerBalanceCalculator.isOpeningInRange(account, range.startInclusive, range.endExclusive) }
            .map(Account::initialBalance)
            .ledgerSumExact()
        val cashInflowJob = async { transactionRepository.sumCashInflowBetween(range.startInclusive, range.endExclusive) }
        val cashOutflowJob = async { transactionRepository.sumCashOutflowBetween(range.startInclusive, range.endExclusive) }
        val reconciliationIncreaseJob = async {
            transactionRepository.sumBalanceUpdateIncreaseBetween(range.startInclusive, range.endExclusive)
        }
        val reconciliationDecreaseJob = async {
            transactionRepository.sumBalanceUpdateDecreaseBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentIncreaseJob = async {
            transactionRepository.sumManualAdjustmentIncreaseBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentDecreaseJob = async {
            transactionRepository.sumManualAdjustmentDecreaseBetween(range.startInclusive, range.endExclusive)
        }
        val cashFlowRecordCountJob = async {
            transactionRepository.countActiveCashFlowRecordsBetween(range.startInclusive, range.endExclusive)
        }
        val transferRecordCountJob = async {
            transactionRepository.countActiveTransferRecordsBetween(range.startInclusive, range.endExclusive)
        }
        val manualAdjustmentRecordCountJob = async {
            transactionRepository.countManualAdjustmentRecordsBetween(range.startInclusive, range.endExclusive)
        }

        val balances = balanceJob.await()
        val openingBalanceByAccount = openingBalanceJob.await()

        HomeProjector.project(
            accounts = allAccounts,
            openAccounts = openAccounts,
            reminderConfigs = reminderConfigs,
            settings = settings,
            dueReminders = dueReminders,
            balances = balances,
            openingBalanceByAccount = openingBalanceByAccount,
            newAccountOpeningAssets = newAccountOpeningAssets,
            cashInflow = cashInflowJob.await(),
            cashOutflow = cashOutflowJob.await(),
            reconciliationIncrease = reconciliationIncreaseJob.await(),
            reconciliationDecrease = reconciliationDecreaseJob.await(),
            manualAdjustmentIncrease = manualAdjustmentIncreaseJob.await(),
            manualAdjustmentDecrease = manualAdjustmentDecreaseJob.await(),
            cashFlowRecordCount = cashFlowRecordCountJob.await(),
            transferRecordCount = transferRecordCountJob.await(),
            manualAdjustmentRecordCount = manualAdjustmentRecordCountJob.await(),
            snapshotTimeMillis = snapshotTimeMillis,
            zoneId = zoneId,
        )
    }
}
