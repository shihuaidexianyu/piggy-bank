package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.migration.StartupMigrationBackend
import com.shihuaidexianyu.money.data.migration.StartupMigrationCoordinator
import com.shihuaidexianyu.money.data.migration.StartupMigrationErrorKind
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.migration.StartupRecoveryAction
import com.shihuaidexianyu.money.data.migration.LegacySourceExport
import com.shihuaidexianyu.money.data.migration.StartupStepResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StartupMigrationCoordinatorTest {
    @Test
    fun `startup recovery API exposes explicit settings reset and raw legacy export`() {
        val actionNames = StartupRecoveryAction.entries.map { it.name }.toSet()
        assertEquals(
            setOf(
                "RETRY",
                "USE_CURRENT_DATABASE",
                "RESET_LOCAL_SETTINGS",
                "EXPORT_LEGACY_SOURCE",
            ),
            actionNames,
        )
        val backendMethods = StartupMigrationBackend::class.java.methods.map { it.name }.toSet()
        val coordinatorMethods = StartupMigrationCoordinator::class.java.methods.map { it.name }.toSet()
        assertTrue("resetCorruptLocalSettings" in backendMethods)
        assertTrue("exportLegacySource" in backendMethods)
        assertTrue("resetCorruptLocalSettings" in coordinatorMethods)
        assertTrue(coordinatorMethods.any { it.startsWith("exportLegacySource") })
    }

    @Test
    fun `corrupt settings stays blocked until explicit reset then reruns to ready`() = runBlocking {
        val backend = FakeBackend(settingsCorrupt = true)
        val coordinator = StartupMigrationCoordinator(backend)

        coordinator.runMigration()
        val error = assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)
        assertEquals(StartupMigrationErrorKind.CORRUPT_SETTINGS, error.kind)
        assertEquals(
            setOf(StartupRecoveryAction.RETRY, StartupRecoveryAction.RESET_LOCAL_SETTINGS),
            error.actions,
        )
        coordinator.retry()
        assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)

        coordinator.resetCorruptLocalSettings()

        assertEquals(1, backend.settingsResetCount)
        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
    }

    @Test
    fun `raw legacy export does not dismiss the migration error`() = runBlocking {
        val backend = FakeBackend(legacyError = StartupMigrationErrorKind.CORRUPT_LEGACY)
        val coordinator = StartupMigrationCoordinator(backend)
        coordinator.runMigration()

        val export = coordinator.exportLegacySource().getOrThrow()

        assertEquals("content://money/legacy.json", export.contentUri)
        assertEquals(1, backend.exportCount)
        assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)
        Unit
    }

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
    fun `before ready recovery hook runs after migrations and its failure keeps ledger gated`() = runBlocking {
        val backend = FakeBackend()
        val coordinator = StartupMigrationCoordinator(backend)
        var shouldFail = true
        coordinator.installBeforeReadyStep {
            backend.calls += "receipts"
            if (shouldFail) error("receipt recovery failed")
        }

        coordinator.runMigration()

        assertIs<StartupMigrationState.RecoverableError>(coordinator.state.value)
        assertEquals(null, coordinator.withReadyLedgerAccess { true })
        assertEquals(listOf("legacy", "settings", "device", "receipts"), backend.calls)

        shouldFail = false
        coordinator.retry()

        assertEquals(StartupMigrationState.Ready, coordinator.state.value)
        assertEquals(true, coordinator.withReadyLedgerAccess { true })
        assertEquals("receipts", backend.calls.last())
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
            override suspend fun resetCorruptLocalSettings() = StartupStepResult.Complete
            override suspend fun exportLegacySource() = error("not used")
        }
        val coordinator = StartupMigrationCoordinator(backend)

        assertFailsWith<CancellationException> { coordinator.runMigration() }
        assertEquals(StartupMigrationState.Loading, coordinator.state.value)
    }

    private class FakeBackend(
        private var crashLegacyBeforeCommit: Boolean = false,
        private var crashSettingsAfterCommit: Boolean = false,
        private val legacyError: StartupMigrationErrorKind? = null,
        private var settingsCorrupt: Boolean = false,
    ) : StartupMigrationBackend {
        val calls = mutableListOf<String>()
        var legacyWrites = 0
        var settingsWrites = 0
        var deviceWrites = 0
        var explicitIgnoreCount = 0
        var settingsResetCount = 0
        var exportCount = 0
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
            if (settingsCorrupt) {
                return StartupStepResult.RecoverableError(
                    StartupMigrationErrorKind.CORRUPT_SETTINGS,
                    "settings corrupt",
                    setOf(StartupRecoveryAction.RETRY, StartupRecoveryAction.RESET_LOCAL_SETTINGS),
                )
            }
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

        override suspend fun resetCorruptLocalSettings(): StartupStepResult {
            settingsResetCount++
            settingsCorrupt = false
            return StartupStepResult.Complete
        }

        override suspend fun exportLegacySource(): LegacySourceExport {
            exportCount++
            return LegacySourceExport("content://money/legacy.json", "legacy.json")
        }
    }
}
