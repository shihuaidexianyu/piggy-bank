package com.shihuaidexianyu.money.di

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.backup.BackupImportCoordinator
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.AiJournaledLedgerUseCase
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountClosureIssuesUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveDueRemindersUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.SetAccountHiddenUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UndoSkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import com.shihuaidexianyu.money.domain.usecase.SyncMoneyNotificationsUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.ExportSyncSnapshotPageUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.GetSyncStateUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.PullSyncChangesUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.PushSyncPatchesUseCase
import com.shihuaidexianyu.money.notification.calculateBalanceCheckBalances
import com.shihuaidexianyu.money.domain.usecase.ResolveNotificationLaunchUseCase

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

    val syncMoneyNotificationsUseCase = SyncMoneyNotificationsUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        publisher = data.moneyNotificationPublisher,
        notificationSyncRequester = data.notificationSyncRequester,
        accountBalanceProvider = { accounts ->
            calculateBalanceCheckBalances(accounts, calculateAccountBalancesUseCase)
        },
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
    )

    val resolveNotificationLaunchUseCase = ResolveNotificationLaunchUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
    )

    val resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
        accountRepository = data.accountRepository,
        ledgerAggregateRepository = data.ledgerAggregateRepository,
    )

    val refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
        accountRepository = data.accountRepository,
        ledgerAggregateRepository = data.ledgerAggregateRepository,
    )

    val appendSyncChangesUseCase = AppendSyncChangesUseCase(
        syncRepository = data.syncRepository,
        clockProvider = SystemClockProvider,
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
        transactionRunner = data.transactionRepository,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val createTransferRecordUseCase = CreateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val updateCashFlowRecordUseCase = UpdateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val deleteCashFlowRecordUseCase = DeleteCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val updateTransferRecordUseCase = UpdateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val deleteTransferRecordUseCase = DeleteTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val updateBalanceUseCase = UpdateBalanceUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val createBalanceAdjustmentUseCase = CreateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val updateBalanceAdjustmentUseCase = UpdateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val deleteBalanceAdjustmentUseCase = DeleteBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val restoreLedgerRecordUseCase = RestoreLedgerRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val aiJournaledLedgerUseCase = AiJournaledLedgerUseCase(
        journalRepository = data.aiMutationJournalRepository,
        transactionRepository = data.transactionRepository,
        createCashFlowRecordUseCase = createCashFlowRecordUseCase,
        updateCashFlowRecordUseCase = updateCashFlowRecordUseCase,
        deleteCashFlowRecordUseCase = deleteCashFlowRecordUseCase,
        createTransferRecordUseCase = createTransferRecordUseCase,
        updateTransferRecordUseCase = updateTransferRecordUseCase,
        deleteTransferRecordUseCase = deleteTransferRecordUseCase,
        restoreLedgerRecordUseCase = restoreLedgerRecordUseCase,
        clockProvider = SystemClockProvider,
    )

    val updateAccountUseCase = UpdateAccountUseCase(
        accountRepository = data.accountRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        transactionRunner = data.transactionRepository,
        accountLifecycleCoordinator = accountLifecycleCoordinator,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val closeAccountUseCase = CloseAccountUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        transactionRunner = data.transactionRepository,
        clockProvider = SystemClockProvider,
        accountLifecycleCoordinator = accountLifecycleCoordinator,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val setAccountHiddenUseCase = SetAccountHiddenUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val reopenAccountUseCase = ReopenAccountUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val observeAccountClosureIssuesUseCase = ObserveAccountClosureIssuesUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
    )

    val updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(
        accountRepository = data.accountRepository,
        transactionRunner = data.transactionRepository,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val createReminderUseCase = CreateReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    val updateReminderUseCase = UpdateReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    val deleteReminderUseCase = DeleteReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    val processDueReminderUseCase = ProcessDueReminderUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        reminderRepository = data.recurringReminderRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
        notificationSyncRequester = data.notificationSyncRequester,
        appendSyncChangesUseCase = appendSyncChangesUseCase,
    )

    val skipReminderUseCase = SkipReminderUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        clockProvider = SystemClockProvider,
        zoneIdProvider = SystemZoneIdProvider,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    val undoSkipReminderUseCase = UndoSkipReminderUseCase(
        reminderRepository = data.recurringReminderRepository,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    val observeDueRemindersUseCase = ObserveDueRemindersUseCase(
        reminderRepository = data.recurringReminderRepository,
    )

    val buildExportSnapshotUseCase = BuildExportSnapshotUseCase(
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        accountRepository = data.accountRepository,
        recurringReminderRepository = data.recurringReminderRepository,
        portableSettingsRepository = data.portableSettingsRepository,
        transactionRepository = data.transactionRepository,
        databaseVersion = MONEY_DATABASE_VERSION,
        clockProvider = SystemClockProvider,
    )

    val buildExportJsonUseCase = BuildExportJsonUseCase(
        buildExportSnapshotUseCase = buildExportSnapshotUseCase,
        backupJsonEncoder = BackupJsonCodec,
    )

    val validateBackupSnapshotUseCase = ValidateBackupSnapshotUseCase(SystemClockProvider)

    val getSyncStateUseCase = GetSyncStateUseCase(
        syncRepository = data.syncRepository,
        clockProvider = SystemClockProvider,
    )

    val exportSyncSnapshotPageUseCase = ExportSyncSnapshotPageUseCase(
        syncRepository = data.syncRepository,
    )

    val pullSyncChangesUseCase = PullSyncChangesUseCase(
        syncRepository = data.syncRepository,
    )

    val pushSyncPatchesUseCase = PushSyncPatchesUseCase(
        journalRepository = data.aiMutationJournalRepository,
        transactionRepository = data.transactionRepository,
        syncRepository = data.syncRepository,
        updateCashFlowRecordUseCase = updateCashFlowRecordUseCase,
        updateTransferRecordUseCase = updateTransferRecordUseCase,
        clockProvider = SystemClockProvider,
    )

    val backupImportCoordinator = BackupImportCoordinator(
        stagedStore = data.stagedBackupStore,
        safetyStore = data.safetySnapshotStore,
        receiptStore = data.importReceiptStore,
        currentSnapshotSource = { exportedAt -> buildExportSnapshotUseCase(exportedAt) },
        backupRepository = data.backupRepository,
        validator = validateBackupSnapshotUseCase,
        clockProvider = SystemClockProvider,
        notificationSyncRequester = data.notificationSyncRequester,
    )

    init {
        data.startupMigrationCoordinator.installBeforeReadyStep {
            backupImportCoordinator.cleanupExpiredStages()
            backupImportCoordinator.recoverPendingReceipts()
        }
    }
}
