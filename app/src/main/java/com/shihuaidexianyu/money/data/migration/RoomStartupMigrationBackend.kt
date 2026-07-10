package com.shihuaidexianyu.money.data.migration

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.db.accountReminderSettingsDataStore
import com.shihuaidexianyu.money.data.db.appSettingsDataStore
import com.shihuaidexianyu.money.data.db.settingsCorruptionDetectedKey
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity
import com.shihuaidexianyu.money.data.entity.PortableSettingsEntity
import com.shihuaidexianyu.money.data.repository.LegacyMoneyStoreReadResult
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import com.shihuaidexianyu.money.data.repository.DevicePreferencesMapper
import com.shihuaidexianyu.money.data.repository.toEntity
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

interface StartupMigrationFaultInjector {
    fun beforeCommit(step: String)
    fun afterCommit(step: String)

    data object None : StartupMigrationFaultInjector {
        override fun beforeCommit(step: String) = Unit
        override fun afterCommit(step: String) = Unit
    }
}

class RoomStartupMigrationBackend(
    private val context: Context,
    private val database: MoneyDatabase,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val clockProvider: ClockProvider,
    private val legacyStore: PersistentMoneyStore = PersistentMoneyStore(context),
    private val faultInjector: StartupMigrationFaultInjector = StartupMigrationFaultInjector.None,
    private val legacySourceExporter: LegacySourceRecoveryExporter = LegacySourceRecoveryExporter(
        context = context,
        sourceFile = legacyStore.storageFile,
        clockProvider = clockProvider,
    ),
) : StartupMigrationBackend {
    private var corruptSettingsSources: Set<LocalSettingsSource> = emptySet()

    override suspend fun migrateLegacy(): StartupStepResult {
        if (isComplete(LEGACY_STEP)) return StartupStepResult.Complete
        return when (val source = legacyStore.readStrict()) {
            LegacyMoneyStoreReadResult.Missing,
            LegacyMoneyStoreReadResult.Empty,
            -> runMarkedStep(LEGACY_STEP, "legacy source absent or empty") {
                StartupStepResult.Complete
            }

            is LegacyMoneyStoreReadResult.Corrupt -> StartupStepResult.RecoverableError(
                StartupMigrationErrorKind.CORRUPT_LEGACY,
                source.diagnostic,
                actions = LEGACY_SOURCE_RECOVERY_ACTIONS,
            )

            is LegacyMoneyStoreReadResult.Data -> runMarkedStep(
                key = LEGACY_STEP,
                detail = "legacy store imported and verified",
            ) {
                if (database.accountDao().queryAllAccounts().isNotEmpty()) {
                    return@runMarkedStep StartupStepResult.RecoverableError(
                        StartupMigrationErrorKind.LEGACY_ROOM_CONFLICT,
                        "检测到旧账本文件与当前 Room 账本同时包含数据，未自动合并。",
                        actions = LEGACY_SOURCE_RECOVERY_ACTIONS,
                    )
                }
                val snapshot = source.snapshot
                snapshot.accounts.sortedBy { it.id }.forEach { database.accountDao().insert(it.toEntity()) }
                snapshot.cashFlowRecords.sortedBy { it.id }.forEach {
                    database.cashFlowRecordDao().insert(it.toEntity())
                }
                snapshot.transferRecords.sortedBy { it.id }.forEach {
                    database.transferRecordDao().insert(it.toEntity())
                }
                snapshot.balanceUpdates.sortedBy { it.id }.forEach {
                    database.balanceUpdateRecordDao().insert(it.toEntity())
                }
                snapshot.adjustments.sortedBy { it.id }.forEach {
                    database.balanceAdjustmentRecordDao().insert(it.toEntity())
                }
                checkIds(snapshot.accounts.map { it.id }, database.accountDao().queryAllAccounts().map { it.id })
                checkIds(snapshot.cashFlowRecords.map { it.id }, database.cashFlowRecordDao().queryAll().map { it.id })
                checkIds(snapshot.transferRecords.map { it.id }, database.transferRecordDao().queryAll().map { it.id })
                checkIds(snapshot.balanceUpdates.map { it.id }, database.balanceUpdateRecordDao().queryAll().map { it.id })
                checkIds(snapshot.adjustments.map { it.id }, database.balanceAdjustmentRecordDao().queryAll().map { it.id })
                StartupStepResult.Complete
            }
        }
    }

    override suspend fun migratePortableSettingsAndReminderConfigs(): StartupStepResult {
        val legacy = runCatching { readLegacySettings() }.getOrElsePreservingCancellation { error ->
            return corruptSettingsError(error.message ?: "无法读取旧设置")
        }
        if (isComplete(PORTABLE_STEP)) return StartupStepResult.Complete
        return runMarkedStep(
            key = PORTABLE_STEP,
            detail = "portable settings and reminder configs migrated",
        ) {
            val portableDao = database.portableSettingsDao()
            if (portableDao.query() == null) {
                portableDao.upsert(legacy.portable.toEntity())
            }
            val reminderDao = database.accountReminderConfigDao()
            val existing = reminderDao.queryAll().associateBy { it.accountId }
            val missing = database.accountDao().queryAllAccounts().mapNotNull { account ->
                if (account.id in existing) return@mapNotNull null
                val config = legacy.reminderConfigs[account.id] ?: BalanceUpdateReminderConfig()
                config.copy(
                    isEnabled = account.closedAt == null,
                    lastNotifiedBoundaryAt = null,
                ).toEntity(account.id)
            }
            if (missing.isNotEmpty()) reminderDao.upsertAll(missing)
            StartupStepResult.Complete
        }
    }

    override suspend fun migrateDevicePreferences(): StartupStepResult {
        val stores = runCatching { readSettingsStores() }.getOrElsePreservingCancellation { error ->
            return corruptSettingsError(error.message ?: "无法读取设备偏好")
        }
        val current = runCatching {
            DevicePreferencesMapper.fromPreferences(stores.first)
        }.getOrElsePreservingCancellation { error ->
            corruptSettingsSources = corruptSettingsSources + LocalSettingsSource.APP
            return corruptSettingsError(error.message ?: "设备偏好格式损坏")
        }
        if (isComplete(DEVICE_STEP)) return StartupStepResult.Complete
        devicePreferencesRepository.replace(current)
        context.appSettingsDataStore.edit { preferences ->
            preferences.remove(LegacyKeys.HomePeriod)
            preferences.remove(LegacyKeys.CurrencySymbol)
            preferences.remove(LegacyKeys.ShowStaleMark)
            preferences.remove(LegacyKeys.AmountColorMode)
            preferences.remove(LegacyKeys.MonthlyBudget)
        }
        context.accountReminderSettingsDataStore.edit { it.clear() }
        return runMarkedStep(
            key = DEVICE_STEP,
            detail = "device preferences shape retained",
        ) {
            StartupStepResult.Complete
        }
    }

    override suspend fun explicitlyIgnoreLegacy(): StartupStepResult = runMarkedStep(
        key = LEGACY_STEP,
        detail = "legacy source explicitly ignored; file preserved",
    ) {
        StartupStepResult.Complete
    }

    override suspend fun resetCorruptLocalSettings(): StartupStepResult {
        val stores = runCatching {
            val app = context.appSettingsDataStore.data.first()
            val reminders = context.accountReminderSettingsDataStore.data.first()
            app to reminders
        }.getOrElsePreservingCancellation { error ->
            return corruptSettingsError(error.message ?: "无法读取待重置的本地设置")
        }
        val sources = buildSet {
            addAll(corruptSettingsSources)
            if (stores.first[settingsCorruptionDetectedKey] == true) add(LocalSettingsSource.APP)
            if (stores.second[settingsCorruptionDetectedKey] == true) add(LocalSettingsSource.REMINDERS)
        }
        if (sources.isEmpty()) {
            return corruptSettingsError("未检测到可重置的损坏设置，请重试读取。")
        }
        return runCatching {
            if (LocalSettingsSource.APP in sources) {
                context.appSettingsDataStore.edit { preferences ->
                    preferences.clear()
                    DevicePreferencesMapper.write(preferences, DevicePreferences())
                }
            }
            if (LocalSettingsSource.REMINDERS in sources) {
                context.accountReminderSettingsDataStore.edit { it.clear() }
            }
            corruptSettingsSources = emptySet()
            StartupStepResult.Complete
        }.getOrElsePreservingCancellation { error ->
            corruptSettingsError(error.message ?: "重置本地设置失败")
        }
    }

    override suspend fun exportLegacySource(): LegacySourceExport = legacySourceExporter.export()

    private suspend fun runMarkedStep(
        key: String,
        detail: String,
        block: suspend () -> StartupStepResult,
    ): StartupStepResult {
        var committed = false
        val result = database.withTransaction {
            if (isComplete(key)) return@withTransaction StartupStepResult.Complete
            when (val stepResult = block()) {
                StartupStepResult.Complete -> {
                    faultInjector.beforeCommit(key)
                    markComplete(key, detail)
                    committed = true
                    StartupStepResult.Complete
                }

                is StartupStepResult.RecoverableError -> stepResult
            }
        }
        if (committed) faultInjector.afterCommit(key)
        return result
    }

    private suspend fun isComplete(key: String): Boolean =
        database.localMigrationStateDao().queryByKey(key)?.state == COMPLETE_STATE

    private suspend fun markComplete(key: String, detail: String) {
        database.localMigrationStateDao().upsert(
            LocalMigrationStateEntity(
                key = key,
                state = COMPLETE_STATE,
                completedAt = clockProvider.nowMillis(),
                detail = detail,
            ),
        )
    }

    private suspend fun readLegacySettings(): LegacySettingsSnapshot {
        val (app, reminders) = readSettingsStores()
        val portable = runCatching {
            PortableSettings(
                currencySymbol = normalizeCurrencySymbol(app[LegacyKeys.CurrencySymbol] ?: "¥"),
                amountColorMode = AmountColorMode.fromValue(app[LegacyKeys.AmountColorMode]),
                monthlyBudgetAmount = app[LegacyKeys.MonthlyBudget]?.takeIf { it > 0L },
            )
        }.getOrElsePreservingCancellation { error ->
            corruptSettingsSources = corruptSettingsSources + LocalSettingsSource.APP
            throw error
        }
        val reminderConfigs = runCatching {
            parseLegacyReminderConfigs(reminders)
        }.getOrElsePreservingCancellation { error ->
            corruptSettingsSources = corruptSettingsSources + LocalSettingsSource.REMINDERS
            throw error
        }
        return LegacySettingsSnapshot(
            portable = portable,
            reminderConfigs = reminderConfigs,
        )
    }

    private suspend fun readSettingsStores(): Pair<Preferences, Preferences> {
        val app = context.appSettingsDataStore.data.first()
        val reminders = context.accountReminderSettingsDataStore.data.first()
        val marked = buildSet {
            if (app[settingsCorruptionDetectedKey] == true) add(LocalSettingsSource.APP)
            if (reminders[settingsCorruptionDetectedKey] == true) add(LocalSettingsSource.REMINDERS)
        }
        if (marked.isNotEmpty()) {
            corruptSettingsSources = corruptSettingsSources + marked
            error("检测到本地设置文件损坏，需要确认重置后才能继续。")
        }
        return app to reminders
    }

    private fun corruptSettingsError(diagnostic: String) = StartupStepResult.RecoverableError(
        kind = StartupMigrationErrorKind.CORRUPT_SETTINGS,
        diagnostic = diagnostic,
        actions = SETTINGS_RECOVERY_ACTIONS,
    )

    private data class LegacySettingsSnapshot(
        val portable: PortableSettings,
        val reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    )

    private companion object {
        const val LEGACY_STEP = "legacy_money_store_v1"
        const val PORTABLE_STEP = "portable_settings_v1"
        const val DEVICE_STEP = "device_preferences_v1"
        const val COMPLETE_STATE = "complete"
        val LEGACY_SOURCE_RECOVERY_ACTIONS = setOf(
            StartupRecoveryAction.RETRY,
            StartupRecoveryAction.USE_CURRENT_DATABASE,
            StartupRecoveryAction.EXPORT_LEGACY_SOURCE,
        )
        val SETTINGS_RECOVERY_ACTIONS = setOf(
            StartupRecoveryAction.RETRY,
            StartupRecoveryAction.RESET_LOCAL_SETTINGS,
        )
    }
}

private enum class LocalSettingsSource {
    APP,
    REMINDERS,
}

private fun checkIds(expected: List<Long>, actual: List<Long>) {
    check(expected.sorted() == actual.sorted()) { "旧账本写入后复读校验失败" }
}

private inline fun <T> Result<T>.getOrElsePreservingCancellation(
    onFailure: (Throwable) -> T,
): T = getOrElse { error ->
    if (error is CancellationException) throw error
    onFailure(error)
}

private object LegacyKeys {
    val HomePeriod = stringPreferencesKey("home_period")
    val CurrencySymbol = stringPreferencesKey("currency_symbol")
    val ShowStaleMark = booleanPreferencesKey("show_stale_mark")
    val AmountColorMode = stringPreferencesKey("amount_color_mode")
    val MonthlyBudget = longPreferencesKey("monthly_budget_amount")
}

private const val PERIOD_KEY_PREFIX = "account_reminder_period_"
private const val WEEKDAY_KEY_PREFIX = "account_reminder_weekday_"
private const val MONTH_DAY_KEY_PREFIX = "account_reminder_month_day_"
private const val TIME_KEY_PREFIX = "account_reminder_time_"

private fun parseLegacyReminderConfigs(preferences: Preferences): Map<Long, BalanceUpdateReminderConfig> {
    val accountIds = preferences.asMap().keys.mapNotNull { key ->
        listOf(PERIOD_KEY_PREFIX, WEEKDAY_KEY_PREFIX, MONTH_DAY_KEY_PREFIX, TIME_KEY_PREFIX)
            .firstOrNull(key.name::startsWith)
            ?.let(key.name::removePrefix)
            ?.toLongOrNull()
    }.toSet()
    return accountIds.associateWith { accountId ->
        val time = preferences[stringPreferencesKey("$TIME_KEY_PREFIX$accountId")]
            ?.split(':')
            .orEmpty()
        BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.fromValue(
                preferences[stringPreferencesKey("$PERIOD_KEY_PREFIX$accountId")],
            ),
            weekday = BalanceUpdateReminderWeekday.fromValue(
                preferences[stringPreferencesKey("$WEEKDAY_KEY_PREFIX$accountId")],
            ),
            monthDay = preferences[stringPreferencesKey("$MONTH_DAY_KEY_PREFIX$accountId")]
                ?.toIntOrNull()
                ?.takeIf { it in 1..31 }
                ?: 1,
            hour = time.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 22,
            minute = time.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0,
            isEnabled = true,
        )
    }
}

private fun PortableSettings.toEntity(): PortableSettingsEntity = PortableSettingsEntity(
    id = 1,
    currencySymbol = currencySymbol,
    amountColorMode = amountColorMode.value,
    monthlyBudgetAmount = monthlyBudgetAmount,
)

private fun BalanceUpdateReminderConfig.toEntity(accountId: Long): AccountReminderConfigEntity =
    AccountReminderConfigEntity(
        accountId = accountId,
        period = period.value,
        weekday = weekday.value,
        monthDay = monthDay,
        hour = hour,
        minute = minute,
        isEnabled = isEnabled,
        lastNotifiedBoundaryAt = lastNotifiedBoundaryAt,
    )
