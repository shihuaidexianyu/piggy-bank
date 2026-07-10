package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.migration.StartupMigrationBackend
import com.shihuaidexianyu.money.data.migration.StartupMigrationCoordinator
import com.shihuaidexianyu.money.data.migration.StartupMigrationErrorKind
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.migration.StartupStepResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class StartupMigrationCoordinatorTest {
    @Test
    fun `startup runs steps in deterministic order and repeated run is idempotent`() = runBlocking {
        val backend = FakeBackend()
        val coordinator = StartupMigrationCoordinator(backend)

        var ledgerReads = 0
        assertEquals(null, coordinator.withReadyLedgerAccess { ++ledgerReads })
        coordinator.runMigration()
        assertEquals(1, coordinator.withReadyLedgerAccess { ++ledgerReads })
        coordinator.runMigration()

        assertEquals(listOf("legacy", "settings", "device", "legacy", "settings", "device"), backend.calls)
        assertEquals(1, backend.legacyWrites)
        assertEquals(1, backend.settingsWrites)
        assertEquals(1, backend.deviceWrites)
        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        assertEquals(1, ledgerReads)
    }

    @Test
    fun `crash before commit retries while crash after commit does not overwrite`() = runBlocking {
        val beforeCommit = FakeBackend(crashLegacyBeforeCommit = true)
        val coordinator = StartupMigrationCoordinator(beforeCommit)

        coordinator.runMigration()
        assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)
        coordinator.retry()
        assertEquals(1, beforeCommit.legacyWrites)
        assertEquals(StartupMigrationState.Ready, coordinator.state.value)

        val afterCommit = FakeBackend(crashSettingsAfterCommit = true)
        val second = StartupMigrationCoordinator(afterCommit)
        second.runMigration()
        assertIs<StartupMigrationState.RecoverableError>(second.state.value)
        second.retry()
        assertEquals(1, afterCommit.settingsWrites)
        assertEquals(StartupMigrationState.Ready, second.state.value)
    }

    @Test
    fun `corrupt or conflicting legacy source remains recoverable until explicit choice`() = runBlocking {
        for (kind in listOf(StartupMigrationErrorKind.CORRUPT_LEGACY, StartupMigrationErrorKind.LEGACY_ROOM_CONFLICT)) {
            val backend = FakeBackend(legacyError = kind)
            val coordinator = StartupMigrationCoordinator(backend)

            coordinator.runMigration()

            val error = assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)
            assertEquals(kind, error.kind)
            assertEquals(0, backend.legacyWrites)
            coordinator.useCurrentDatabaseAndIgnoreLegacy()
            assertEquals(1, backend.explicitIgnoreCount)
            assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        }
    }

    @Test
    fun `startup never converts coroutine cancellation into a recoverable error`() = runBlocking {
        val backend = object : StartupMigrationBackend {
            override suspend fun migrateLegacy(): StartupStepResult = throw CancellationException("cancel")
            override suspend fun migratePortableSettingsAndReminderConfigs() = StartupStepResult.Complete
            override suspend fun migrateDevicePreferences() = StartupStepResult.Complete
            override suspend fun explicitlyIgnoreLegacy() = StartupStepResult.Complete
        }
        val coordinator = StartupMigrationCoordinator(backend)

        assertFailsWith<CancellationException> { coordinator.runMigration() }
        assertEquals(StartupMigrationState.Loading, coordinator.state.value)
    }

    private class FakeBackend(
        private var crashLegacyBeforeCommit: Boolean = false,
        private var crashSettingsAfterCommit: Boolean = false,
        private val legacyError: StartupMigrationErrorKind? = null,
    ) : StartupMigrationBackend {
        val calls = mutableListOf<String>()
        var legacyWrites = 0
        var settingsWrites = 0
        var deviceWrites = 0
        var explicitIgnoreCount = 0
        private var legacyComplete = false
        private var settingsComplete = false
        private var deviceComplete = false

        override suspend fun migrateLegacy(): StartupStepResult {
            calls += "legacy"
            legacyError?.takeUnless { legacyComplete }?.let {
                return StartupStepResult.RecoverableError(it, "legacy error")
            }
            if (legacyComplete) return StartupStepResult.Complete
            if (crashLegacyBeforeCommit) {
                crashLegacyBeforeCommit = false
                error("before commit")
            }
            legacyWrites++
            legacyComplete = true
            return StartupStepResult.Complete
        }

        override suspend fun migratePortableSettingsAndReminderConfigs(): StartupStepResult {
            calls += "settings"
            if (settingsComplete) return StartupStepResult.Complete
            settingsWrites++
            settingsComplete = true
            if (crashSettingsAfterCommit) {
                crashSettingsAfterCommit = false
                error("after commit")
            }
            return StartupStepResult.Complete
        }

        override suspend fun migrateDevicePreferences(): StartupStepResult {
            calls += "device"
            if (!deviceComplete) {
                deviceWrites++
                deviceComplete = true
            }
            return StartupStepResult.Complete
        }

        override suspend fun explicitlyIgnoreLegacy(): StartupStepResult {
            explicitIgnoreCount++
            legacyComplete = true
            return StartupStepResult.Complete
        }
    }
}
