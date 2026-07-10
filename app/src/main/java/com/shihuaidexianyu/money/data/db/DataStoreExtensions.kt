package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.preferencesDataStore

val settingsCorruptionDetectedKey = booleanPreferencesKey("corruption_detected")

val settingsDataStoreCorruptionHandler: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler {
        mutablePreferencesOf(settingsCorruptionDetectedKey to true)
    }

val Context.appSettingsDataStore by preferencesDataStore(
    name = "app_settings",
    corruptionHandler = settingsDataStoreCorruptionHandler,
)
val Context.accountReminderSettingsDataStore by preferencesDataStore(
    name = "account_reminder_settings",
    corruptionHandler = settingsDataStoreCorruptionHandler,
)

