package com.shihuaidexianyu.money

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.db.accountReminderSettingsDataStore
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.data.migration.RoomStartupMigrationBackend
import com.shihuaidexianyu.money.data.migration.StartupMigrationCoordinator
import com.shihuaidexianyu.money.data.migration.StartupMigrationFaultInjector
import com.shihuaidexianyu.money.data.migration.StartupMigrationErrorKind
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.DevicePreferencesRepositoryImpl
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import com.shihuaidexianyu.money.data.repository.PortableSettingsRepositoryImpl
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupMigrationRoomContractTest {
    private lateinit var context: Context
    private lateinit var database: MoneyDatabase
    private lateinit var legacyFile: File

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.appSettingsDataStore.edit { it.clear() }
        context.accountReminderSettingsDataStore.edit { it.clear() }
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        legacyFile = File(context.cacheDir, "legacy-${System.nanoTime()}.json")
    }

    @After
    fun tearDown() {
        database.close()
        legacyFile.delete()
    }

    @Test
    fun oldDataStoresMapOnceAndRepeatedStartupDoesNotOverwriteRoomChanges() = runBlocking {
        val accountId = AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        context.appSettingsDataStore.edit {
            it[stringPreferencesKey("currency_symbol")] = " 元 "
            it[stringPreferencesKey("amount_color_mode")] = "green_income_red_expense"
            it[stringPreferencesKey("theme_mode")] = "dark"
            it[booleanPreferencesKey("biometric_lock")] = true
            it[stringPreferencesKey("last_history_keyword")] = "咖啡"
            it[longPreferencesKey("last_history_account_id")] = accountId
        }
        context.accountReminderSettingsDataStore.edit {
            it[stringPreferencesKey("account_reminder_period_$accountId")] = "monthly"
            it[stringPreferencesKey("account_reminder_month_day_$accountId")] = "20"
            it[stringPreferencesKey("account_reminder_time_$accountId")] = "08:30"
        }
        val coordinator = coordinator()

        coordinator.runMigration()

        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        val portable = PortableSettingsRepositoryImpl(database, database.portableSettingsDao())
        assertEquals("元", portable.query().currencySymbol)
        assertEquals(AmountColorMode.GREEN_INCOME_RED_EXPENSE, portable.query().amountColorMode)
        val reminder = AccountReminderSettingsRepositoryImpl(
            database,
            database.accountReminderConfigDao(),
        ).getReminderConfig(accountId)
        assertEquals(BalanceUpdateReminderPeriod.MONTHLY, reminder.period)
        assertEquals(20, reminder.monthDay)
        assertEquals(8, reminder.hour)
        assertEquals(30, reminder.minute)
        assertTrue(reminder.isEnabled)
        assertNull(reminder.lastNotifiedBoundaryAt)
        val device = DevicePreferencesRepositoryImpl(context).query()
        assertEquals(ThemeMode.DARK, device.themeMode)
        assertTrue(device.biometricLock)
        assertEquals("咖啡", device.historyFilters.keyword)
        assertEquals(accountId, device.historyFilters.accountId)
        assertEquals(3, database.localMigrationStateDao().queryAll().size)
        val remainingAppKeys = context.appSettingsDataStore.data.first().asMap().keys.map { it.name }
        assertTrue("currency_symbol" !in remainingAppKeys)
        assertTrue("amount_color_mode" !in remainingAppKeys)
        assertTrue("home_period" !in remainingAppKeys)
        assertTrue("show_stale_mark" !in remainingAppKeys)
        assertTrue(context.accountReminderSettingsDataStore.data.first().asMap().isEmpty())

        portable.updateCurrencySymbol("US$")
        coordinator.runMigration()
        assertEquals("US$", portable.query().currencySymbol)
    }

    @Test
    fun corruptLegacyAndNonemptyConflictNeverChangeEitherSource() = runBlocking {
        legacyFile.writeText("{broken")
        val corrupt = coordinator()
        corrupt.runMigration()
        val corruptState = corrupt.state.value as StartupMigrationState.RecoverableError
        assertEquals(StartupMigrationErrorKind.CORRUPT_LEGACY, corruptState.kind)
        assertTrue(database.accountDao().queryAllAccounts().isEmpty())
        assertNull(database.localMigrationStateDao().queryByKey("legacy_money_store_v1"))

        legacyFile.writeText(
            """{"accounts":[{"id":2,"name":"旧账户","initialBalance":0,"createdAt":1,"displayOrder":0}]}""",
        )
        AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "当前账户", initialBalance = 0L, createdAt = 1L),
        )
        val conflict = coordinator()
        conflict.runMigration()
        val conflictState = conflict.state.value as StartupMigrationState.RecoverableError
        assertEquals(StartupMigrationErrorKind.LEGACY_ROOM_CONFLICT, conflictState.kind)
        assertEquals(listOf("当前账户"), database.accountDao().queryAllAccounts().map { it.name })
        assertTrue(legacyFile.exists())
        assertTrue(legacyFile.readText().contains("旧账户"))
    }

    @Test
    fun settingsStepCrashBoundariesRollbackBeforeCommitAndSkipOverwriteAfterCommit() = runBlocking {
        val accountId = AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        context.appSettingsDataStore.edit {
            it[stringPreferencesKey("currency_symbol")] = "旧值"
        }
        val beforeFault = OneShotFault("portable_settings_v1", afterCommit = false)
        val before = coordinator(beforeFault)
        before.runMigration()
        assertTrue(before.state.value is StartupMigrationState.RecoverableError)
        assertNull(database.portableSettingsDao().query())
        assertNull(database.accountReminderConfigDao().queryByAccountId(accountId))
        assertNull(database.localMigrationStateDao().queryByKey("portable_settings_v1"))
        before.retry()
        assertEquals(StartupMigrationState.Ready, before.state.value)

        database.portableSettingsDao().deleteAll()
        database.localMigrationStateDao().deleteAll()
        context.appSettingsDataStore.edit {
            it[stringPreferencesKey("currency_symbol")] = "提交值"
        }
        val afterFault = OneShotFault("portable_settings_v1", afterCommit = true)
        val after = coordinator(afterFault)
        after.runMigration()
        assertTrue(after.state.value is StartupMigrationState.RecoverableError)
        val portable = PortableSettingsRepositoryImpl(database, database.portableSettingsDao())
        portable.updateCurrencySymbol("用户新值")
        after.retry()
        assertEquals(StartupMigrationState.Ready, after.state.value)
        assertEquals("用户新值", portable.query().currencySymbol)
    }

    private fun coordinator(
        faultInjector: StartupMigrationFaultInjector = StartupMigrationFaultInjector.None,
    ): StartupMigrationCoordinator = StartupMigrationCoordinator(
        RoomStartupMigrationBackend(
            context = context,
            database = database,
            devicePreferencesRepository = DevicePreferencesRepositoryImpl(context),
            clockProvider = ClockProvider { 123L },
            legacyStore = PersistentMoneyStore(legacyFile),
            faultInjector = faultInjector,
        ),
    )

    private class OneShotFault(
        private val targetStep: String,
        private val afterCommit: Boolean,
    ) : StartupMigrationFaultInjector {
        private var fired = false

        override fun beforeCommit(step: String) {
            if (!afterCommit && !fired && step == targetStep) {
                fired = true
                error("before commit")
            }
        }

        override fun afterCommit(step: String) {
            if (afterCommit && !fired && step == targetStep) {
                fired = true
                error("after commit")
            }
        }
    }
}
