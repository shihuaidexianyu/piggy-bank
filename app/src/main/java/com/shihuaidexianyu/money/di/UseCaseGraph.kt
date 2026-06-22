package com.shihuaidexianyu.money.di

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.domain.usecase.ArchiveAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
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
import com.shihuaidexianyu.money.domain.usecase.ImportBackupUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveDueRemindersUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
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
    val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
    )

    val calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(
        transactionRepository = data.transactionRepository,
    )

    val resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
    )

    val refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
    )

    val observeHomeDashboardUseCase = ObserveHomeDashboardUseCase(
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        accountRepository = data.accountRepository,
        recurringReminderRepository = data.recurringReminderRepository,
        settingsRepository = data.settingsRepository,
        transactionRepository = data.transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
    )

    val observeStatsDashboardUseCase = ObserveStatsDashboardUseCase(
        accountRepository = data.accountRepository,
        settingsRepository = data.settingsRepository,
        transactionRepository = data.transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        calculateAccountBalancesUseCase = calculateAccountBalancesUseCase,
    )

    fun observeAccountDetailUseCase(accountId: Long): ObserveAccountDetailUseCase {
        return ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = data.accountReminderSettingsRepository,
            accountRepository = data.accountRepository,
            settingsRepository = data.settingsRepository,
            transactionRepository = data.transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        )
    }

    val createAccountUseCase = CreateAccountUseCase(
        accountRepository = data.accountRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
    )

    val createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val createTransferRecordUseCase = CreateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateCashFlowRecordUseCase = UpdateCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteCashFlowRecordUseCase = DeleteCashFlowRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateTransferRecordUseCase = UpdateTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteTransferRecordUseCase = DeleteTransferRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateBalanceUseCase = UpdateBalanceUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val createBalanceAdjustmentUseCase = CreateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateBalanceAdjustmentUseCase = UpdateBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteBalanceAdjustmentUseCase = DeleteBalanceAdjustmentUseCase(
        accountRepository = data.accountRepository,
        transactionRepository = data.transactionRepository,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateAccountUseCase = UpdateAccountUseCase(
        accountRepository = data.accountRepository,
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
    )

    val archiveAccountUseCase = ArchiveAccountUseCase(
        accountRepository = data.accountRepository,
        reminderRepository = data.recurringReminderRepository,
        transactionRepository = data.transactionRepository,
    )

    val updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(
        accountRepository = data.accountRepository,
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
    )

    val observeDueRemindersUseCase = ObserveDueRemindersUseCase(
        reminderRepository = data.recurringReminderRepository,
    )

    val buildExportSnapshotUseCase = BuildExportSnapshotUseCase(
        accountReminderSettingsRepository = data.accountReminderSettingsRepository,
        accountRepository = data.accountRepository,
        recurringReminderRepository = data.recurringReminderRepository,
        settingsRepository = data.settingsRepository,
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
