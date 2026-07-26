package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.DashboardPeriod
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import java.time.ZoneId

/**
 * Pure projection from raw ledger inputs to a [HomeDashboardSnapshot]. Extracted from
 * [ObserveHomeDashboardUseCase] so the math can be unit-tested without I/O.
 */
internal object HomeProjector {
    fun project(
        accounts: List<Account>,
        openAccounts: List<Account> = accounts,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: PortableSettings,
        dueReminders: List<RecurringReminder>,
        balances: Map<Long, Long>,
        openingBalanceByAccount: Map<Long, Long>,
        newAccountOpeningAssets: Long,
        cashInflow: Long,
        cashOutflow: Long,
        reconciliationIncrease: Long,
        reconciliationDecrease: Long,
        manualAdjustmentIncrease: Long,
        manualAdjustmentDecrease: Long,
        cashFlowRecordCount: Int,
        transferRecordCount: Int,
        manualAdjustmentRecordCount: Int,
        recentRecords: List<HistoryRecord> = emptyList(),
        netWorthTrend: List<NetWorthTrendPoint> = emptyList(),
        period: DashboardPeriod = DashboardPeriod.DEFAULT,
        previousCashInflow: Long = 0L,
        previousCashOutflow: Long = 0L,
        monthCashOutflow: Long = cashOutflow,
        monthProgressDays: PeriodProgressDays = PeriodProgressDays(elapsed = 1, total = 1),
        reconciliationNetByAccount: Map<Long, Long> = emptyMap(),
        snapshotTimeMillis: Long,
        zoneId: ZoneId,
    ): HomeDashboardSnapshot {
        val totalAssets = balances.values.ledgerSumExact()
        val openingTotalAssets = ledgerAddExact(
            openingBalanceByAccount.values.ledgerSumExact(),
            newAccountOpeningAssets,
        )
        val periodBreakdown = PeriodAssetBreakdown(
            openingAssets = openingTotalAssets,
            closingAssets = totalAssets,
            assetChange = ledgerSubtractExact(totalAssets, openingTotalAssets),
            cashInflow = cashInflow,
            cashOutflow = cashOutflow,
            manualAdjustmentIncrease = manualAdjustmentIncrease,
            manualAdjustmentDecrease = manualAdjustmentDecrease,
            reconciliationIncrease = reconciliationIncrease,
            reconciliationDecrease = reconciliationDecrease,
        )
        val staleAccounts = openAccounts.filter { account ->
            val reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig()
            if (!reminderConfig.isEnabled) return@filter false
            AccountStatusCalculator.isStale(
                account,
                reminderConfig = reminderConfig,
                nowMillis = snapshotTimeMillis,
                zoneId = zoneId,
            )
        }
        val monthlyBudget = calculateMonthlyBudgetStatus(
            targetAmount = settings.monthlyBudgetAmount,
            spentAmount = monthCashOutflow,
        )
        return HomeDashboardSnapshot(
            settings = settings,
            totalAssets = totalAssets,
            periodBreakdown = periodBreakdown,
            periodRecordCount = cashFlowRecordCount + transferRecordCount + manualAdjustmentRecordCount,
            staleAccountCount = staleAccounts.size,
            openAccounts = openAccounts,
            staleAccounts = staleAccounts,
            accountBalances = balances,
            dueReminders = dueReminders,
            monthlyBudget = monthlyBudget,
            recentRecords = recentRecords,
            hasAnyAccounts = accounts.isNotEmpty(),
            allAccountCount = accounts.size,
            netWorthTrend = netWorthTrend,
            period = period,
            // Measured against the period's own opening rather than the previous period's closing:
            // both numbers are already known here, so the comparison costs no extra ledger reads.
            netWorthDelta = calculatePeriodDelta(
                currentAmount = totalAssets,
                baselineAmount = openingTotalAssets,
            ),
            cashInflowDelta = calculatePeriodDelta(
                currentAmount = cashInflow,
                baselineAmount = previousCashInflow,
            ),
            cashOutflowDelta = calculatePeriodDelta(
                currentAmount = cashOutflow,
                baselineAmount = previousCashOutflow,
            ),
            budgetPace = monthlyBudget?.let { budget ->
                calculateBudgetPace(
                    targetAmount = budget.targetAmount,
                    spentAmount = budget.spentAmount,
                    daysElapsed = monthProgressDays.elapsed,
                    daysTotal = monthProgressDays.total,
                )
            },
            hasInvestmentAccounts = accounts.any(Account::isInvestment),
            // Closed investment accounts still count toward the split (their balance is zero by
            // the closing rule) and their historical deltas still count as P&L for past periods.
            investmentAssets = accounts
                .filter(Account::isInvestment)
                .map { balances[it.id] ?: 0L }
                .ledgerSumExact(),
            periodInvestmentPnl = accounts
                .filter(Account::isInvestment)
                .map { reconciliationNetByAccount[it.id] ?: 0L }
                .ledgerSumExact(),
        )
    }
}
