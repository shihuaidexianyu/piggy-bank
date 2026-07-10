package com.shihuaidexianyu.money.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.shihuaidexianyu.money.data.backup.BackupFileReader
import com.shihuaidexianyu.money.data.backup.BackupRepositoryImpl
import com.shihuaidexianyu.money.data.backup.ImportReceiptStore
import com.shihuaidexianyu.money.data.backup.SafetySnapshotStore
import com.shihuaidexianyu.money.data.backup.StagedBackupStore
import com.shihuaidexianyu.money.data.debug.DebugSampleDataSeeder
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.DevicePreferencesRepositoryImpl
import com.shihuaidexianyu.money.data.repository.PortableSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.NotificationSyncingAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.RecurringReminderRepositoryImpl
import com.shihuaidexianyu.money.data.repository.SavingsGoalRepositoryImpl
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.data.migration.RoomStartupMigrationBackend
import com.shihuaidexianyu.money.data.migration.StartupMigrationCoordinator
import com.shihuaidexianyu.money.notification.AndroidMoneyNotificationPublisher
import com.shihuaidexianyu.money.notification.DefaultMoneyNotificationContentPolicy
import com.shihuaidexianyu.money.notification.WorkManagerNotificationSyncRequester

internal class DataGraph(context: Context) {
    private val appContext = context.applicationContext

    val notificationSyncRequester = WorkManagerNotificationSyncRequester(appContext)

    val moneyDatabase: MoneyDatabase = MoneyDatabase.getInstance(appContext)

    val accountRepository: AccountRepository =
        AccountRepositoryImpl(moneyDatabase.accountDao())

    private val transactionRepositoryImpl = TransactionRepositoryImpl(
        database = moneyDatabase,
        cashFlowRecordDao = moneyDatabase.cashFlowRecordDao(),
        transferRecordDao = moneyDatabase.transferRecordDao(),
        balanceUpdateRecordDao = moneyDatabase.balanceUpdateRecordDao(),
        balanceAdjustmentRecordDao = moneyDatabase.balanceAdjustmentRecordDao(),
        historyRecordDao = moneyDatabase.historyRecordDao(),
        ledgerAggregateDao = moneyDatabase.ledgerAggregateDao(),
    )

    val transactionRepository: TransactionRepository = transactionRepositoryImpl

    val ledgerAggregateRepository: LedgerAggregateRepository = transactionRepositoryImpl

    val portableSettingsRepository = PortableSettingsRepositoryImpl(
        database = moneyDatabase,
        dao = moneyDatabase.portableSettingsDao(),
    )

    val devicePreferencesRepository = DevicePreferencesRepositoryImpl(appContext)

    val accountReminderSettingsRepository: AccountReminderSettingsRepository =
        NotificationSyncingAccountReminderSettingsRepository(
            delegate = AccountReminderSettingsRepositoryImpl(
            database = moneyDatabase,
            dao = moneyDatabase.accountReminderConfigDao(),
            ),
            requester = notificationSyncRequester,
        )

    val startupMigrationCoordinator = StartupMigrationCoordinator(
        RoomStartupMigrationBackend(
            context = appContext,
            database = moneyDatabase,
            devicePreferencesRepository = devicePreferencesRepository,
            clockProvider = SystemClockProvider,
        ),
    )

    val recurringReminderRepository: RecurringReminderRepository =
        RecurringReminderRepositoryImpl(moneyDatabase.recurringReminderDao())

    val moneyNotificationPublisher = AndroidMoneyNotificationPublisher(
        context = appContext,
        contentPolicy = DefaultMoneyNotificationContentPolicy(portableSettingsRepository),
    )

    val savingsGoalRepository: SavingsGoalRepository =
        SavingsGoalRepositoryImpl(
            savingsGoalDao = moneyDatabase.savingsGoalDao(),
        )

    val backupRepository: BackupRepository =
        BackupRepositoryImpl(
            database = moneyDatabase,
            portableSettingsRepository = portableSettingsRepository,
            accountReminderSettingsRepository = accountReminderSettingsRepository,
        )

    val exportJsonFileWriter = ExportJsonFileWriter(appContext, SystemZoneIdProvider)

    val stagedBackupStore = StagedBackupStore(appContext.cacheDir)

    val safetySnapshotStore = SafetySnapshotStore(appContext.filesDir)

    val importReceiptStore = ImportReceiptStore(appContext.filesDir)

    val backupFileReader = BackupFileReader(appContext, stagedBackupStore)

    suspend fun seedDebugSampleDataIfNeeded() {
        startupMigrationCoordinator.withReadyLedgerAccess { true } ?: return
        val isDebuggableApp = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggableApp) {
            DebugSampleDataSeeder.seedIfNeeded(appContext, moneyDatabase)
        }
    }
}
