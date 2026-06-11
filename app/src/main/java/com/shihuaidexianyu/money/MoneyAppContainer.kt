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

    val settingsRepository get() = dataGraph.settingsRepository

    val accountReminderSettingsRepository get() = dataGraph.accountReminderSettingsRepository

    val recurringReminderRepository get() = dataGraph.recurringReminderRepository

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

    val updateAccountUseCase get() = useCaseGraph.updateAccountUseCase

    val archiveAccountUseCase get() = useCaseGraph.archiveAccountUseCase

    val updateAccountDisplayOrderUseCase get() = useCaseGraph.updateAccountDisplayOrderUseCase

    val createReminderUseCase get() = useCaseGraph.createReminderUseCase

    val updateReminderUseCase get() = useCaseGraph.updateReminderUseCase

    val deleteReminderUseCase get() = useCaseGraph.deleteReminderUseCase

    val confirmReminderUseCase get() = useCaseGraph.confirmReminderUseCase

    val processDueReminderUseCase get() = useCaseGraph.processDueReminderUseCase

    val observeDueRemindersUseCase get() = useCaseGraph.observeDueRemindersUseCase

    val buildExportSnapshotUseCase get() = useCaseGraph.buildExportSnapshotUseCase

    val buildExportJsonUseCase get() = useCaseGraph.buildExportJsonUseCase

    val validateBackupSnapshotUseCase get() = useCaseGraph.validateBackupSnapshotUseCase

    val importBackupUseCase get() = useCaseGraph.importBackupUseCase

    val exportJsonFileWriter get() = dataGraph.exportJsonFileWriter

    val backupFileReader get() = dataGraph.backupFileReader

    val preImportBackupWriter get() = dataGraph.preImportBackupWriter
}
