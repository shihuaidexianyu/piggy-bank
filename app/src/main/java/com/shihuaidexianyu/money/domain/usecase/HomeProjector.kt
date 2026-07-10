package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
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
        )
    }
}
