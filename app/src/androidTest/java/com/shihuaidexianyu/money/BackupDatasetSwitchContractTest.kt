package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.backup.BackupRepositoryImpl
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.PortableSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.SyncRepositoryImpl
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_HOUR
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupAccountReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupPortableSettings
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.model.sync.NewSyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.time.ClockProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-Room contract for the backup-replace dataset switch (design 5.6): importing a backup
 * replaces the portable ledger with different bytes, so the sync dataset must be reset inside the
 * same transaction — new datasetId, cleared change-log, nextRevision back to 1 — forcing any
 * paired mirror to re-snapshot instead of pulling changes against a stale cursor.
 */
@RunWith(AndroidJUnit4::class)
class BackupDatasetSwitchContractTest {
    private lateinit var database: MoneyDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replaceAllSwitchesDatasetAndClearsChangeLog() = runBlocking {
        val clock = ClockProvider { 9_000L }
        val syncRepository = SyncRepositoryImpl(
            syncDao = database.syncDao(),
            clockProvider = clock,
            datasetIdGenerator = { "ds-original" },
        )
        val backupRepository = BackupRepositoryImpl(
            database = database,
            portableSettingsRepository = PortableSettingsRepositoryImpl(
                database,
                database.portableSettingsDao(),
            ),
            accountReminderSettingsRepository = AccountReminderSettingsRepositoryImpl(
                database,
                database.accountReminderConfigDao(),
            ),
            syncRepository = syncRepository,
            clockProvider = clock,
            datasetIdGenerator = { "ds-imported" },
        )

        // Pre-import state: one account, one change-log entry at revision 1.
        val accountId = database.accountDao().insert(
            AccountEntity(name = "旧账户", initialBalance = 0, createdAt = 100),
        )
        val revision = syncRepository.appendChange(
            NewSyncChange(
                entityKind = SyncEntityKind.ACCOUNT,
                recordId = accountId,
                operation = SyncChangeOperation.UPSERT,
                payloadJson = "{}",
                updatedAt = 500,
            ),
        )
        assertEquals(1L, revision)
        assertEquals("ds-original", syncRepository.readDatasetState().datasetId)

        backupRepository.replaceAll(importedSnapshot())

        // Dataset switched: new id, counter back to 1, change-log and journal wiped.
        val state = syncRepository.readDatasetState()
        assertEquals("ds-imported", state.datasetId)
        assertEquals(1L, state.nextRevision)
        assertTrue(syncRepository.queryChangesAfter(0, 10).isEmpty())
        assertNull(syncRepository.queryLatestRevisionFor(SyncEntityKind.ACCOUNT, accountId))

        // The imported ledger replaced the old one in the same transaction.
        assertNull(database.accountDao().queryById(accountId))
        val imported = database.accountDao().queryById(9)
        assertNotNull(imported)
        assertEquals("导入账户", imported?.name)
        Unit
    }

    private fun importedSnapshot() = MoneyBackupSnapshot(
        metadata = BackupMetadata(
            schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
            databaseVersion = MONEY_DATABASE_VERSION,
            exportedAt = 8_000,
        ),
        portableSettings = BackupPortableSettings(
            currencySymbol = "¥",
            amountColorMode = "red_income_green_expense",
        ),
        accounts = listOf(
            BackupAccount(
                id = 9,
                name = "导入账户",
                initialBalance = 100,
                createdAt = 1_000,
                lastUsedAt = null,
                lastBalanceUpdateAt = null,
                displayOrder = 0,
                colorName = "blue",
            ),
        ),
        cashFlowRecords = emptyList(),
        transferRecords = emptyList(),
        balanceUpdateRecords = emptyList(),
        balanceAdjustmentRecords = emptyList(),
        recurringReminders = emptyList(),
        // The post-import re-read synthesizes one reminder config per account from stored rows or
        // fallback defaults, so the snapshot must carry that same default row to round-trip.
        accountReminderConfigs = listOf(
            BackupAccountReminderConfig(
                accountId = 9,
                config = BackupBalanceUpdateReminderConfig(
                    weekday = BalanceUpdateReminderWeekday.FRIDAY.value,
                    hour = DEFAULT_BALANCE_UPDATE_REMINDER_HOUR,
                    minute = DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE,
                    isEnabled = true,
                ),
            ),
        ),
    )
}
