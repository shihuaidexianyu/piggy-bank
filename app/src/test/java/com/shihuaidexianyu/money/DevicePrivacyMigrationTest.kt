package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevicePrivacyMigrationTest {
    @Test
    fun `existing biometric user gets external privacy defaults atomically once`() = runBlocking {
        val repository = InMemoryDevicePreferencesRepository(
            initial = DevicePreferences(
                biometricLock = true,
                maskAmountsInApp = false,
                hideRecentTasks = false,
                hideWidgetAmounts = false,
                hideNotificationAmounts = false,
            ),
        )

        repository.migrateExternalPrivacyDefaultsIfNeeded()

        val migrated = repository.query()
        assertFalse(migrated.maskAmountsInApp)
        assertTrue(migrated.hideRecentTasks)
        assertTrue(migrated.hideWidgetAmounts)
        assertTrue(migrated.hideNotificationAmounts)

        repository.updateHideRecentTasks(false)
        repository.updateHideWidgetAmounts(false)
        repository.updateHideNotificationAmounts(false)
        repository.migrateExternalPrivacyDefaultsIfNeeded()

        assertEquals(
            DevicePreferences(biometricLock = true),
            repository.query(),
            "durable marker must preserve explicit post-migration choices",
        )
    }

    @Test
    fun `non biometric user records marker without enabling privacy`() = runBlocking {
        val repository = InMemoryDevicePreferencesRepository()

        repository.migrateExternalPrivacyDefaultsIfNeeded()
        repository.updateBiometricLock(true)
        repository.migrateExternalPrivacyDefaultsIfNeeded()

        assertEquals(DevicePreferences(biometricLock = true), repository.query())
    }
}
