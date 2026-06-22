package com.shihuaidexianyu.money.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.shihuaidexianyu.money.data.backup.BackupFileReader
import com.shihuaidexianyu.money.data.backup.BackupRepositoryImpl
import com.shihuaidexianyu.money.data.backup.PreImportBackupWriter
import com.shihuaidexianyu.money.data.debug.DebugSampleDataSeeder
import com.shihuaidexianyu.money.data.db.LegacyMoneyStoreImporter
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.RecurringReminderRepositoryImpl
import com.shihuaidexianyu.money.data.repository.SettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.BackupRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class DataGraph(context: Context) {
    private val appContext = context.applicationContext

    val moneyDatabase: MoneyDatabase = MoneyDatabase.getInstance(appContext)

    init {
        // Import the legacy file-store format on a background dispatcher. Previously this used
        // `runBlocking` which blocked the constructing thread (typically the main thread) at app
        // startup. Now we kick it off asynchronously — Room's DB is already initialized above,
        // and the legacy importer is idempotent (it checks "DB already has data" before doing
        // anything), so a delayed import just means the legacy data appears a moment later.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                LegacyMoneyStoreImporter.importIfNeeded(
                    context = appContext,
                    database = moneyDatabase,
                )
            }.onFailure { e ->
                android.util.Log.e("DataGraph", "Legacy money store import failed", e)
            }
        }
    }

    val accountRepository: AccountRepository =
        AccountRepositoryImpl(moneyDatabase.accountDao())

    val transactionRepository: TransactionRepository =
        TransactionRepositoryImpl(
            database = moneyDatabase,
            cashFlowRecordDao = moneyDatabase.cashFlowRecordDao(),
            transferRecordDao = moneyDatabase.transferRecordDao(),
            balanceUpdateRecordDao = moneyDatabase.balanceUpdateRecordDao(),
            balanceAdjustmentRecordDao = moneyDatabase.balanceAdjustmentRecordDao(),
            historyRecordDao = moneyDatabase.historyRecordDao(),
        )

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(appContext)

    val accountReminderSettingsRepository: AccountReminderSettingsRepository =
        AccountReminderSettingsRepositoryImpl(appContext)

    val recurringReminderRepository: RecurringReminderRepository =
        RecurringReminderRepositoryImpl(moneyDatabase.recurringReminderDao())

    val backupRepository: BackupRepository =
        BackupRepositoryImpl(
            database = moneyDatabase,
            settingsRepository = settingsRepository,
            accountReminderSettingsRepository = accountReminderSettingsRepository,
        )

    val exportJsonFileWriter = ExportJsonFileWriter(appContext)

    val backupFileReader = BackupFileReader(appContext)

    val preImportBackupWriter = PreImportBackupWriter(appContext)

    suspend fun seedDebugSampleDataIfNeeded() {
        val isDebuggableApp = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggableApp) {
            DebugSampleDataSeeder.seedIfNeeded(appContext, moneyDatabase)
        }
    }
}
