package com.shihuaidexianyu.money

import android.content.Context
import com.shihuaidexianyu.money.di.DataGraph
import com.shihuaidexianyu.money.di.UseCaseGraph

class MoneyAppContainer(context: Context) {
    private val dataGraph = DataGraph(context)
    private val useCaseGraph = UseCaseGraph(dataGraph)

    suspend fun seedDebugSampleDataIfNeeded() {
        dataGraph.seedDebugSampleDataIfNeeded()
    }

    val accountRepository get() = dataGraph.accountRepository

    val transactionRepository get() = dataGraph.transactionRepository

    val portableSettingsRepository get() = dataGraph.portableSettingsRepository

    val devicePreferencesRepository get() = dataGraph.devicePreferencesRepository

    val startupMigrationCoordinator get() = dataGraph.startupMigrationCoordinator

    val accountReminderSettingsRepository get() = dataGraph.accountReminderSettingsRepository

    val recurringReminderRepository get() = dataGraph.recurringReminderRepository

    val savingsGoalRepository get() = dataGraph.savingsGoalRepository

    val backupRepository get() = dataGraph.backupRepository

    val calculateCurrentBalanceUseCase get() = useCaseGraph.calculateCurrentBalanceUseCase

    val calculateAccountBalancesUseCase get() = useCaseGraph.calculateAccountBalancesUseCase

    val resolveBalanceUpdateContextUseCase get() = useCaseGraph.resolveBalanceUpdateContextUseCase

    val refreshAccountActivityStateUseCase get() = useCaseGraph.refreshAccountActivityStateUseCase

    val observeHomeDashboardUseCase get() = useCaseGraph.observeHomeDashboardUseCase

    val observeStatsDashboardUseCase get() = useCaseGraph.observeStatsDashboardUseCase

    fun observeAccountDetailUseCase(accountId: Long) =
        useCaseGraph.observeAccountDetailUseCase(accountId)

    val createAccountUseCase get() = useCaseGraph.createAccountUseCase

    val createCashFlowRecordUseCase get() = useCaseGraph.createCashFlowRecordUseCase

    val createTransferRecordUseCase get() = useCaseGraph.createTransferRecordUseCase

    val updateCashFlowRecordUseCase get() = useCaseGraph.updateCashFlowRecordUseCase

    val deleteCashFlowRecordUseCase get() = useCaseGraph.deleteCashFlowRecordUseCase

    val updateTransferRecordUseCase get() = useCaseGraph.updateTransferRecordUseCase

    val deleteTransferRecordUseCase get() = useCaseGraph.deleteTransferRecordUseCase

    val updateBalanceUseCase get() = useCaseGraph.updateBalanceUseCase

    val updateBalanceUpdateRecordUseCase get() = useCaseGraph.updateBalanceUpdateRecordUseCase

    val deleteBalanceUpdateRecordUseCase get() = useCaseGraph.deleteBalanceUpdateRecordUseCase

    val createBalanceAdjustmentUseCase get() = useCaseGraph.createBalanceAdjustmentUseCase

    val updateBalanceAdjustmentUseCase get() = useCaseGraph.updateBalanceAdjustmentUseCase

    val deleteBalanceAdjustmentUseCase get() = useCaseGraph.deleteBalanceAdjustmentUseCase

    val restoreLedgerRecordUseCase get() = useCaseGraph.restoreLedgerRecordUseCase

    val updateAccountUseCase get() = useCaseGraph.updateAccountUseCase

    val closeAccountUseCase get() = useCaseGraph.closeAccountUseCase

    val setAccountHiddenUseCase get() = useCaseGraph.setAccountHiddenUseCase

    val reopenAccountUseCase get() = useCaseGraph.reopenAccountUseCase

    val observeAccountClosureIssuesUseCase get() = useCaseGraph.observeAccountClosureIssuesUseCase

    val updateAccountDisplayOrderUseCase get() = useCaseGraph.updateAccountDisplayOrderUseCase

    val createReminderUseCase get() = useCaseGraph.createReminderUseCase

    val updateReminderUseCase get() = useCaseGraph.updateReminderUseCase

    val deleteReminderUseCase get() = useCaseGraph.deleteReminderUseCase

    val confirmReminderUseCase get() = useCaseGraph.confirmReminderUseCase

    val processDueReminderUseCase get() = useCaseGraph.processDueReminderUseCase

    val observeDueRemindersUseCase get() = useCaseGraph.observeDueRemindersUseCase

    val observeSavingsGoalUseCase get() = useCaseGraph.observeSavingsGoalUseCase

    val upsertSavingsGoalUseCase get() = useCaseGraph.upsertSavingsGoalUseCase

    val clearSavingsGoalUseCase get() = useCaseGraph.clearSavingsGoalUseCase

    val buildExportSnapshotUseCase get() = useCaseGraph.buildExportSnapshotUseCase

    val buildExportJsonUseCase get() = useCaseGraph.buildExportJsonUseCase

    val validateBackupSnapshotUseCase get() = useCaseGraph.validateBackupSnapshotUseCase

    val backupImportCoordinator get() = useCaseGraph.backupImportCoordinator

    val exportJsonFileWriter get() = dataGraph.exportJsonFileWriter

    val backupFileReader get() = dataGraph.backupFileReader

}
