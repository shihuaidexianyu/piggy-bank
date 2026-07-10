package com.shihuaidexianyu.money.di

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ImportBackupUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountClosureIssuesUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveDueRemindersUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.SetAccountHiddenUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase

internal class UseCaseGraph(
    private val data: DataGraph,
) {
    private val accountLifecycleCoordinator = AccountLifecycleCoordinator()

    val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
        accountRepository = data.accountRepository,
        ledgerAggregateRepository = data.ledgerAggregateRepository,
        clockProvider = SystemClockProvider,
    )

    val calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(
        ledgerAggregateRepository = data.ledgerAggregateRepository,
        clockProvider = SystemClockProvider,
    )

    val resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
        accountRepository = data.accountRepository,
        ledgerAggregateRepository = data.ledgerAggregateRepository,
    )

    val refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
        accountRepository = data.accountRepository,
        ledgerAggregateRepository = data.ledgerAggregateRepository,
    )

    val observeHomeDashboardUseCase = ObserveHomeDashboardUseCase(
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        accountRepository = data.accountRepository,
        recurringReminderRepository = data.recurringReminderRepository,
        portableSettingsRepository = data.portableSettingsRepository,
        transactionRepository = data.transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
    )

    val observeStatsDashboardUseCase = ObserveStatsDashboardUseCase(
        accountRepository = data.accountRepository,
        portableSettingsRepository = data.portableSettingsRepository,
        transactionRepository = data.transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
        zoneIdProvider = SystemZoneIdProvider,
    )

    fun observeAccountDetailUseCase(accountId: Long): ObserveAccountDetailUseCase {
        return ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = data.accountReminderSettingsRepository,
            accountRepository = data.accountRepository,
            portableSettingsRepository = data.portableSettingsRepository,
            transactionRepository = data.transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
            clockProvider = SystemClockProvider,
            zoneIdProvider = SystemZoneIdProvider,
        )
    }

    val createAccountUseCase = CreateAccountUseCase(
        accountRepository = data.accountRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        clockProvider = SystemClockProvider,
    )

    val createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val createTransferRecordUseCase = CreateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateCashFlowRecordUseCase = UpdateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val deleteCashFlowRecordUseCase = DeleteCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateTransferRecordUseCase = UpdateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val deleteTransferRecordUseCase = DeleteTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateBalanceUseCase = UpdateBalanceUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val createBalanceAdjustmentUseCase = CreateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateBalanceAdjustmentUseCase = UpdateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val deleteBalanceAdjustmentUseCase = DeleteBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val restoreLedgerRecordUseCase = RestoreLedgerRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateAccountUseCase = UpdateAccountUseCase(
        accountRepository = data.accountRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        transactionRunner = data.transactionRepository,
        accountLifecycleCoordinator = accountLifecycleCoordinator,
    )

    val closeAccountUseCase = CloseAccountUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        transactionRunner = data.transactionRepository,
        clockProvider = SystemClockProvider,
        accountLifecycleCoordinator = accountLifecycleCoordinator,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
    )

    val setAccountHiddenUseCase = SetAccountHiddenUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
    )

    val reopenAccountUseCase = ReopenAccountUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
    )

    val observeAccountClosureIssuesUseCase = ObserveAccountClosureIssuesUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
    )

    val updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
    )

    val createReminderUseCase = CreateReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
    )

    val updateReminderUseCase = UpdateReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
    )

    val deleteReminderUseCase = DeleteReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
    )

    val confirmReminderUseCase = ConfirmReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
    )

    val processDueReminderUseCase = ProcessDueReminderUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        reminderRepository = data.recurringReminderRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
    )

    val observeDueRemindersUseCase = ObserveDueRemindersUseCase(
        reminderRepository = data.recurringReminderRepository,
    )

    val observeSavingsGoalUseCase = ObserveSavingsGoalUseCase(
        accountRepository = data.accountRepository,
        savingsGoalRepository = data.savingsGoalRepository,
        transactionRepository = data.transactionRepository,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
    )

    val upsertSavingsGoalUseCase = UpsertSavingsGoalUseCase(
        savingsGoalRepository = data.savingsGoalRepository,
        clockProvider = SystemClockProvider,
    )

    val clearSavingsGoalUseCase = ClearSavingsGoalUseCase(
        savingsGoalRepository = data.savingsGoalRepository,
    )

    val buildExportSnapshotUseCase = BuildExportSnapshotUseCase(
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        accountRepository = data.accountRepository,
        recurringReminderRepository = data.recurringReminderRepository,
        savingsGoalRepository = data.savingsGoalRepository,
        portableSettingsRepository = data.portableSettingsRepository,
        transactionRepository = data.transactionRepository,
        databaseVersion = MONEY_DATABASE_VERSION,
    )

    val buildExportJsonUseCase = BuildExportJsonUseCase(
        buildExportSnapshotUseCase = buildExportSnapshotUseCase,
        backupJsonEncoder = BackupJsonCodec,
    )

    val validateBackupSnapshotUseCase = ValidateBackupSnapshotUseCase()

    val importBackupUseCase = ImportBackupUseCase(
        backupRepository = data.backupRepository,
        validateBackupSnapshotUseCase = validateBackupSnapshotUseCase,
    )
}
