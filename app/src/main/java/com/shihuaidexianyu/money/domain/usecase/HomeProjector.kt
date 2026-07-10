package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import java.time.ZoneId

/**
 * Pure projection from raw ledger inputs to a [HomeDashboardSnapshot]. Extracted from
 * [ObserveHomeDashboardUseCase] so the math can be unit-tested without I/O.
 */
internal object HomeProjector {
    fun project(
        accounts: List<Account>,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
        settings: AppSettings,
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
        snapshotTimeMillis: Long,
        zoneId: ZoneId,
    ): HomeDashboardSnapshot {
        val totalAssets = balances.values.sum()
        val openingTotalAssets = openingBalanceByAccount.values.sum() + newAccountOpeningAssets
        val periodBreakdown = PeriodAssetBreakdown(
            openingAssets = openingTotalAssets,
            closingAssets = totalAssets,
            assetChange = totalAssets - openingTotalAssets,
            cashInflow = cashInflow,
            cashOutflow = cashOutflow,
            manualAdjustmentIncrease = manualAdjustmentIncrease,
            manualAdjustmentDecrease = manualAdjustmentDecrease,
            reconciliationIncrease = reconciliationIncrease,
            reconciliationDecrease = reconciliationDecrease,
        )
        val staleAccounts = accounts.filter { account ->
            AccountStatusCalculator.isStale(
                account,
                reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig(),
                nowMillis = snapshotTimeMillis,
                zoneId = zoneId,
            )
        }
        return HomeDashboardSnapshot(
            settings = settings,
            totalAssets = totalAssets,
            periodBreakdown = periodBreakdown,
            periodRecordCount = cashFlowRecordCount + transferRecordCount + manualAdjustmentRecordCount,
            staleAccountCount = staleAccounts.size,
            activeAccounts = accounts,
            staleAccounts = staleAccounts,
            accountBalances = balances,
            dueReminders = dueReminders,
        )
    }
}
