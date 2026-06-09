package com.shihuaidexianyu.money.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.db.accountReminderSettingsDataStore
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_HOUR
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AccountReminderSettingsRepositoryImpl(
    private val context: Context,
) : AccountReminderSettingsRepository {
    override fun observeReminderConfigs(): Flow<Map<Long, BalanceUpdateReminderConfig>> {
        return context.accountReminderSettingsDataStore.data.map(::preferencesToReminderConfigs)
    }

    override suspend fun getReminderConfig(accountId: Long): BalanceUpdateReminderConfig {
        val preferences = context.accountReminderSettingsDataStore.data.first()
        return BalanceUpdateReminderConfig(
            weekday = BalanceUpdateReminderWeekday.fromValue(
                preferences[weekdayKeyFor(accountId)] ?: DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY,
            ),
            hour = parseHourMinute(preferences[timeKeyFor(accountId)]).first,
            minute = parseHourMinute(preferences[timeKeyFor(accountId)]).second,
        )
    }

    override suspend fun updateReminderConfig(accountId: Long, config: BalanceUpdateReminderConfig) {
        context.accountReminderSettingsDataStore.edit { preferences ->
            writeReminderConfig(preferences, accountId, config)
        }
    }

    override suspend fun replaceReminderConfigs(configs: Map<Long, BalanceUpdateReminderConfig>) {
        context.accountReminderSettingsDataStore.edit { preferences ->
            preferences.asMap().keys
                .filter { it.name.startsWith(WEEKDAY_KEY_PREFIX) || it.name.startsWith(TIME_KEY_PREFIX) }
                .map { stringPreferencesKey(it.name) }
                .forEach { preferences.remove(it) }
            configs.forEach { (accountId, config) ->
                writeReminderConfig(preferences, accountId, config)
            }
        }
    }

    private fun preferencesToReminderConfigs(preferences: Preferences): Map<Long, BalanceUpdateReminderConfig> {
        val accountIds = buildSet {
            preferences.asMap().keys.forEach { key ->
                when {
                    key.name.startsWith(WEEKDAY_KEY_PREFIX) -> key.name.removePrefix(WEEKDAY_KEY_PREFIX).toLongOrNull()?.let(::add)
                    key.name.startsWith(TIME_KEY_PREFIX) -> key.name.removePrefix(TIME_KEY_PREFIX).toLongOrNull()?.let(::add)
                }
            }
        }
        return buildMap {
            accountIds.forEach { accountId ->
                val weekday = BalanceUpdateReminderWeekday.fromValue(
                    preferences[weekdayKeyFor(accountId)] ?: DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY,
                )
                val (hour, minute) = parseHourMinute(preferences[timeKeyFor(accountId)])
                put(accountId, BalanceUpdateReminderConfig(weekday = weekday, hour = hour, minute = minute))
            }
        }
    }

    private fun parseHourMinute(value: String?): Pair<Int, Int> {
        val parts = value?.split(":")
        val hour = parts?.getOrNull(0)?.toIntOrNull()
        val minute = parts?.getOrNull(1)?.toIntOrNull()
        return if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
            hour to minute
        } else {
            DEFAULT_BALANCE_UPDATE_REMINDER_HOUR to DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE
        }
    }

    private fun defaultTimeValue(): String {
        return "${DEFAULT_BALANCE_UPDATE_REMINDER_HOUR.toString().padStart(2, '0')}:${DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE.toString().padStart(2, '0')}"
    }

    private fun writeReminderConfig(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        accountId: Long,
        config: BalanceUpdateReminderConfig,
    ) {
        val weekdayKey = weekdayKeyFor(accountId)
        val timeKey = timeKeyFor(accountId)
        if (config.weekday.value == DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY) {
            preferences.remove(weekdayKey)
        } else {
            preferences[weekdayKey] = config.weekday.value
        }

        val defaultTime = defaultTimeValue()
        val currentTime = config.timeText
        if (currentTime == defaultTime) {
            preferences.remove(timeKey)
        } else {
            preferences[timeKey] = currentTime
        }
    }

    private fun weekdayKeyFor(accountId: Long) = stringPreferencesKey("$WEEKDAY_KEY_PREFIX$accountId")

    private fun timeKeyFor(accountId: Long) = stringPreferencesKey("$TIME_KEY_PREFIX$accountId")

    private companion object {
        const val WEEKDAY_KEY_PREFIX = "account_reminder_weekday_"
        const val TIME_KEY_PREFIX = "account_reminder_time_"
    }
}

