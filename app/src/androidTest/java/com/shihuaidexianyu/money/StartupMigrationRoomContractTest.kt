package com.shihuaidexianyu.money

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
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
import com.shihuaidexianyu.money.data.migration.LegacySourceRecoveryExporter
import com.shihuaidexianyu.money.data.migration.StartupMigrationCoordinator
import com.shihuaidexianyu.money.data.migration.StartupMigrationFaultInjector
import com.shihuaidexianyu.money.data.migration.StartupMigrationErrorKind
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.migration.StartupRecoveryAction
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.DevicePreferencesRepositoryImpl
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import com.shihuaidexianyu.money.data.repository.PortableSettingsRepositoryImpl
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject

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
        legacyFile.writeText(
            emptyLegacyRoot()
                .put("cashFlowRecords", "damaged")
                .toString(),
        )
        val corrupt = coordinator()
        corrupt.runMigration()
        val corruptState = corrupt.state.value as StartupMigrationState.RecoverableError
        assertEquals(StartupMigrationErrorKind.CORRUPT_LEGACY, corruptState.kind)
        assertTrue(StartupRecoveryAction.EXPORT_LEGACY_SOURCE in corruptState.actions)
        assertTrue(database.accountDao().queryAllAccounts().isEmpty())
        assertNull(database.localMigrationStateDao().queryByKey("legacy_money_store_v1"))

        legacyFile.writeText(
            emptyLegacyRoot()
                .put("accounts", JSONArray().put(accountJson(2L, "旧账户")))
                .toString(),
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
    fun sourceInvariantFailureIsCorruptBeforeRoomAndRawSourceCanBeExported() = runBlocking {
        val rawSource = emptyLegacyRoot()
            .put("accounts", JSONArray().put(accountJson(1L, "现金")))
            .put(
                "cashFlowRecords",
                JSONArray().put(
                    JSONObject()
                        .put("id", 1L)
                        .put("accountId", 99L)
                        .put("direction", "inflow")
                        .put("amount", 1L)
                        .put("purpose", "孤立记录")
                        .put("occurredAt", 2L)
                        .put("createdAt", 2L)
                        .put("updatedAt", 2L),
                ),
            )
            .toString()
        legacyFile.writeText(rawSource)
        val coordinator = coordinator()

        coordinator.runMigration()

        val error = coordinator.state.value as StartupMigrationState.RecoverableError
        assertEquals(StartupMigrationErrorKind.CORRUPT_LEGACY, error.kind)
        assertEquals(
            setOf(
                StartupRecoveryAction.RETRY,
                StartupRecoveryAction.USE_CURRENT_DATABASE,
                StartupRecoveryAction.EXPORT_LEGACY_SOURCE,
            ),
            error.actions,
        )
        assertTrue(database.accountDao().queryAllAccounts().isEmpty())
        assertNull(database.localMigrationStateDao().queryByKey("legacy_money_store_v1"))

        val export = coordinator.exportLegacySource().getOrThrow()
        val exportedText = requireNotNull(
            context.contentResolver.openInputStream(Uri.parse(export.contentUri)),
        ).bufferedReader().use { it.readText() }
        assertEquals(rawSource, exportedText)
        assertTrue(export.fileName.startsWith("legacy-money-store-"))
        assertTrue(export.fileName.endsWith(".json"))
        val secondExport = coordinator.exportLegacySource().getOrThrow()
        assertNotEquals(export.fileName, secondExport.fileName)
        assertNotEquals(export.contentUri, secondExport.contentUri)
        assertTrue(coordinator.state.value is StartupMigrationState.RecoverableError)
    }

    @Test
    fun corruptionMarkerBlocksCompletedStepsUntilExplicitSettingsReset() = runBlocking {
        val accountId = AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        val portable = PortableSettingsRepositoryImpl(database, database.portableSettingsDao())
        portable.updateCurrencySymbol("US$")
        val reminderRepository = AccountReminderSettingsRepositoryImpl(
            database,
            database.accountReminderConfigDao(),
        )
        reminderRepository.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(
                period = BalanceUpdateReminderPeriod.MONTHLY,
                monthDay = 18,
            ),
        )
        listOf("legacy_money_store_v1", "portable_settings_v1", "device_preferences_v1")
            .forEachIndexed { index, key ->
                database.localMigrationStateDao().upsert(
                    LocalMigrationStateEntity(
                        key = key,
                        state = "complete",
                        completedAt = index.toLong() + 1L,
                        detail = "already valid $key",
                    ),
                )
            }
        val statesBefore = database.localMigrationStateDao().queryAll().associateBy { it.key }
        context.appSettingsDataStore.edit {
            it.clear()
            it[booleanPreferencesKey("corruption_detected")] = true
        }
        val coordinator = coordinator()

        coordinator.runMigration()

        val error = coordinator.state.value as StartupMigrationState.RecoverableError
        assertEquals(StartupMigrationErrorKind.CORRUPT_SETTINGS, error.kind)
        assertEquals(
            setOf(StartupRecoveryAction.RETRY, StartupRecoveryAction.RESET_LOCAL_SETTINGS),
            error.actions,
        )
        assertEquals(listOf("现金"), database.accountDao().queryAllAccounts().map { it.name })
        assertEquals("US$", portable.query().currencySymbol)
        assertEquals(18, reminderRepository.getReminderConfig(accountId).monthDay)

        coordinator.retry()
        assertTrue(coordinator.state.value is StartupMigrationState.RecoverableError)
        coordinator.resetCorruptLocalSettings()

        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        assertEquals(DevicePreferencesRepositoryImpl(context).query(), com.shihuaidexianyu.money.domain.model.DevicePreferences())
        assertEquals("US$", portable.query().currencySymbol)
        assertEquals(18, reminderRepository.getReminderConfig(accountId).monthDay)
        assertEquals(statesBefore, database.localMigrationStateDao().queryAll().associateBy { it.key })
        assertTrue(
            context.appSettingsDataStore.data.first()[booleanPreferencesKey("corruption_detected")] != true,
        )
    }

    @Test
    fun corruptLegacyReminderPreferencesResetToSafeDefaultsWithoutChangingLedger() = runBlocking {
        val accountId = AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        context.accountReminderSettingsDataStore.edit {
            it.clear()
            it[booleanPreferencesKey("corruption_detected")] = true
        }
        val coordinator = coordinator()

        coordinator.runMigration()
        assertEquals(
            StartupMigrationErrorKind.CORRUPT_SETTINGS,
            (coordinator.state.value as StartupMigrationState.RecoverableError).kind,
        )
        coordinator.resetCorruptLocalSettings()

        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        assertEquals(listOf("现金"), database.accountDao().queryAllAccounts().map { it.name })
        val config = AccountReminderSettingsRepositoryImpl(
            database,
            database.accountReminderConfigDao(),
        ).getReminderConfig(accountId)
        assertEquals(BalanceUpdateReminderConfig(), config)
        assertTrue(
            context.accountReminderSettingsDataStore.data.first()[booleanPreferencesKey("corruption_detected")] != true,
        )
    }

    @Test
    fun productionCorruptionHandlerMarksARealMalformedPreferencesFile() = runBlocking {
        val file = File(context.cacheDir, "corrupt-${System.nanoTime()}.preferences_pb")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        @Suppress("UNCHECKED_CAST")
        val handler = Class.forName(
            "com.shihuaidexianyu.money.data.db.DataStoreExtensionsKt",
        ).getMethod("getSettingsDataStoreCorruptionHandler")
            .invoke(null) as ReplaceFileCorruptionHandler<Preferences>
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = handler,
                scope = scope,
                produceFile = { file },
            )

            val recovered = store.data.first()

            assertTrue(recovered[booleanPreferencesKey("corruption_detected")] == true)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun failedRawSourceCopyRemovesPartialCacheFile() = runBlocking {
        legacyFile.writeText("raw")
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val filesBefore = exportDirectory.listFiles().orEmpty().map { it.name }.toSet()
        val constructor = LegacySourceRecoveryExporter::class.java.declaredConstructors
            .single { it.parameterCount == 4 }
        val failingCopy: (File, File) -> Unit = { _, destination ->
            destination.writeText("partial")
            error("injected copy failure")
        }
        @Suppress("UNCHECKED_CAST")
        val exporter = constructor.newInstance(
            context,
            legacyFile,
            ClockProvider { 777L },
            failingCopy,
        ) as LegacySourceRecoveryExporter

        var failure: Throwable? = null
        try {
            exporter.export()
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(filesBefore, exportDirectory.listFiles().orEmpty().map { it.name }.toSet())
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

    private fun emptyLegacyRoot(): JSONObject = JSONObject()
        .put("accounts", JSONArray())
        .put("cashFlowRecords", JSONArray())
        .put("transferRecords", JSONArray())
        .put("balanceUpdates", JSONArray())
        .put("adjustments", JSONArray())

    private fun accountJson(id: Long, name: String): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("initialBalance", 0L)
        .put("createdAt", 1L)
        .put("displayOrder", id.toInt())
}
