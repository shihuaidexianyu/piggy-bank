package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.backup.BackupRepositoryImpl
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.data.repository.SavingsGoalRepositoryImpl
import com.shihuaidexianyu.money.domain.model.SAVINGS_GOAL_ID
import com.shihuaidexianyu.money.domain.time.MutationTimestampOverflowException
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupPortableSettings
import com.shihuaidexianyu.money.domain.model.backup.BackupSavingsGoal
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsGoalRoomContractTest {
    private lateinit var database: MoneyDatabase
    private lateinit var repository: SavingsGoalRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SavingsGoalRepositoryImpl(database.savingsGoalDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun singletonUpsert_isAtomicMonotonicAndIdempotent() = runBlocking {
        assertNull(repository.query())
        coroutineScope {
            repeat(20) { index ->
                launch(Dispatchers.Default) { repository.upsert(index + 1L, index + 1L) }
            }
        }
        val first = repository.query()
        assertNotNull(first)
        assertEquals(SAVINGS_GOAL_ID, first?.id)
        assertTrue(first?.targetAmount in 1L..20L)

        val beforeSame = requireNotNull(repository.query())
        repository.upsert(beforeSame.targetAmount, now = 0L)
        assertEquals(beforeSame, repository.query())
        repository.upsert(targetAmount = 100L, now = 0L)
        val changed = requireNotNull(repository.query())
        assertEquals(beforeSame.createdAt, changed.createdAt)
        assertEquals(beforeSame.updatedAt + 1L, changed.updatedAt)

        repository.clear()
        repository.clear()
        assertNull(repository.query())
    }

    @Test
    fun singletonUpsert_rejectsInvalidAndMaxTimestampChange() = runBlocking {
        try {
            repository.upsert(0L, 1L)
            throw AssertionError("Expected invalid target")
        } catch (_: IllegalArgumentException) {
        }
        repository.upsert(1L, Long.MAX_VALUE)
        try {
            repository.upsert(2L, Long.MAX_VALUE)
            throw AssertionError("Expected timestamp overflow")
        } catch (_: MutationTimestampOverflowException) {
        }
    }

    @Test
    fun v4Import_persistsSingletonGoalWithFixedId() = runBlocking {
        val backupRepository = BackupRepositoryImpl(
            database = database,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
        )
        backupRepository.replaceAll(
            emptySnapshot().copy(
                savingsGoal = BackupSavingsGoal(
                    id = SAVINGS_GOAL_ID,
                    targetAmount = 200L,
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            ),
        )

        val goal = requireNotNull(repository.query())
        assertEquals(SAVINGS_GOAL_ID, goal.id)
        assertEquals(200L, goal.targetAmount)
        assertEquals(2L, goal.createdAt)
    }

    @Test
    fun importReplacement_neverClearsDevicePreferencesOrLocalMigrationState() = runBlocking {
        database.localMigrationStateDao().upsert(
            LocalMigrationStateEntity("startup", "complete", 1L, "kept"),
        )
        val devicePreferences = InMemoryDevicePreferencesRepository(
            DevicePreferences(themeMode = ThemeMode.DARK, biometricLock = true),
        )
        val backupRepository = BackupRepositoryImpl(
            database = database,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
        )

        backupRepository.replaceAll(emptySnapshot())

        assertEquals(ThemeMode.DARK, devicePreferences.query().themeMode)
        assertTrue(devicePreferences.query().biometricLock)
        assertEquals("complete", database.localMigrationStateDao().queryByKey("startup")?.state)
    }

    private fun emptySnapshot(): MoneyBackupSnapshot = MoneyBackupSnapshot(
        metadata = BackupMetadata(
            schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
            databaseVersion = 14,
            exportedAt = 1L,
        ),
        portableSettings = BackupPortableSettings(
            currencySymbol = "¥",
            amountColorMode = "red_income_green_expense",
            monthlyBudgetAmount = null,
        ),
        accounts = emptyList(),
        cashFlowRecords = emptyList(),
        transferRecords = emptyList(),
        balanceUpdateRecords = emptyList(),
        balanceAdjustmentRecords = emptyList(),
        recurringReminders = emptyList(),
        accountReminderConfigs = emptyList(),
        savingsGoal = null,
    )
}
