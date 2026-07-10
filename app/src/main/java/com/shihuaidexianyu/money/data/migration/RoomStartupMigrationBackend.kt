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
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity
import com.shihuaidexianyu.money.data.entity.PortableSettingsEntity
import com.shihuaidexianyu.money.data.repository.LegacyMoneyStoreReadResult
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import com.shihuaidexianyu.money.data.repository.toEntity
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import kotlinx.coroutines.flow.first

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
) : StartupMigrationBackend {
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
            )

            is LegacyMoneyStoreReadResult.Data -> runMarkedStep(
                key = LEGACY_STEP,
                detail = "legacy store imported and verified",
            ) {
                if (database.accountDao().queryAllAccounts().isNotEmpty()) {
                    return@runMarkedStep StartupStepResult.RecoverableError(
                        StartupMigrationErrorKind.LEGACY_ROOM_CONFLICT,
                        "检测到旧账本文件与当前 Room 账本同时包含数据，未自动合并。",
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
        if (isComplete(PORTABLE_STEP)) return StartupStepResult.Complete
        val legacy = runCatching { readLegacySettings() }.getOrElse { error ->
            return StartupStepResult.RecoverableError(
                StartupMigrationErrorKind.CORRUPT_SETTINGS,
                error.message ?: "无法读取旧设置",
                actions = setOf(StartupRecoveryAction.RETRY),
            )
        }
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
        if (isComplete(DEVICE_STEP)) return StartupStepResult.Complete
        val current = runCatching { devicePreferencesRepository.query() }.getOrElse { error ->
            return StartupStepResult.RecoverableError(
                StartupMigrationErrorKind.CORRUPT_SETTINGS,
                error.message ?: "无法读取设备偏好",
                actions = setOf(StartupRecoveryAction.RETRY),
            )
        }
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
        val app = context.appSettingsDataStore.data.first()
        val reminders = context.accountReminderSettingsDataStore.data.first()
        return LegacySettingsSnapshot(
            portable = PortableSettings(
                currencySymbol = normalizeCurrencySymbol(app[LegacyKeys.CurrencySymbol] ?: "¥"),
                amountColorMode = AmountColorMode.fromValue(app[LegacyKeys.AmountColorMode]),
                monthlyBudgetAmount = app[LegacyKeys.MonthlyBudget]?.takeIf { it > 0L },
            ),
            reminderConfigs = parseLegacyReminderConfigs(reminders),
        )
    }

    private data class LegacySettingsSnapshot(
        val portable: PortableSettings,
        val reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    )

    private companion object {
        const val LEGACY_STEP = "legacy_money_store_v1"
        const val PORTABLE_STEP = "portable_settings_v1"
        const val DEVICE_STEP = "device_preferences_v1"
        const val COMPLETE_STATE = "complete"
    }
}

private fun checkIds(expected: List<Long>, actual: List<Long>) {
    check(expected.sorted() == actual.sorted()) { "旧账本写入后复读校验失败" }
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
