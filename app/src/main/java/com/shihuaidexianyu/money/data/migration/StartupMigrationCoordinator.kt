package com.shihuaidexianyu.money.data.migration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

enum class StartupMigrationErrorKind {
    CORRUPT_LEGACY,
    LEGACY_ROOM_CONFLICT,
    CORRUPT_SETTINGS,
    UNEXPECTED,
}

enum class StartupRecoveryAction {
    RETRY,
    USE_CURRENT_DATABASE,
}

sealed interface StartupMigrationState {
    data object Loading : StartupMigrationState
    data object Ready : StartupMigrationState
    data class RecoverableError(
        val kind: StartupMigrationErrorKind,
        val diagnostic: String,
        val actions: Set<StartupRecoveryAction> = setOf(
            StartupRecoveryAction.RETRY,
            StartupRecoveryAction.USE_CURRENT_DATABASE,
        ),
    ) : StartupMigrationState
}

sealed interface StartupStepResult {
    data object Complete : StartupStepResult
    data class RecoverableError(
        val kind: StartupMigrationErrorKind,
        val diagnostic: String,
        val actions: Set<StartupRecoveryAction> = setOf(
            StartupRecoveryAction.RETRY,
            StartupRecoveryAction.USE_CURRENT_DATABASE,
        ),
    ) : StartupStepResult
}

interface StartupMigrationBackend {
    suspend fun migrateLegacy(): StartupStepResult
    suspend fun migratePortableSettingsAndReminderConfigs(): StartupStepResult
    suspend fun migrateDevicePreferences(): StartupStepResult
    suspend fun explicitlyIgnoreLegacy(): StartupStepResult
}

class StartupMigrationCoordinator(
    private val backend: StartupMigrationBackend,
) {
    private val mutableState = MutableStateFlow<StartupMigrationState>(StartupMigrationState.Loading)
    val state: StateFlow<StartupMigrationState> = mutableState.asStateFlow()
    private val mutex = Mutex()

    val isReady: Boolean get() = state.value == StartupMigrationState.Ready

    inline fun <T> withReadyLedgerAccess(block: () -> T): T? =
        if (isReady) block() else null

    suspend fun runMigration() = mutex.withLock {
        mutableState.value = StartupMigrationState.Loading
        runSteps()
    }

    suspend fun retry() = runMigration()

    suspend fun useCurrentDatabaseAndIgnoreLegacy() = mutex.withLock {
        mutableState.value = StartupMigrationState.Loading
        when (val ignored = safely { backend.explicitlyIgnoreLegacy() }) {
            StartupStepResult.Complete -> runRemainingSteps()
            is StartupStepResult.RecoverableError -> publish(ignored)
        }
    }

    private suspend fun runSteps() {
        when (val result = safely { backend.migrateLegacy() }) {
            StartupStepResult.Complete -> runRemainingSteps()
            is StartupStepResult.RecoverableError -> publish(result)
        }
    }

    private suspend fun runRemainingSteps() {
        when (val settings = safely { backend.migratePortableSettingsAndReminderConfigs() }) {
            StartupStepResult.Complete -> Unit
            is StartupStepResult.RecoverableError -> return publish(settings)
        }
        when (val device = safely { backend.migrateDevicePreferences() }) {
            StartupStepResult.Complete -> mutableState.value = StartupMigrationState.Ready
            is StartupStepResult.RecoverableError -> publish(device)
        }
    }

    private suspend inline fun safely(block: () -> StartupStepResult): StartupStepResult =
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            StartupStepResult.RecoverableError(
                kind = StartupMigrationErrorKind.UNEXPECTED,
                diagnostic = error.message ?: error::class.simpleName.orEmpty(),
                actions = setOf(StartupRecoveryAction.RETRY),
            )
        }

    private fun publish(error: StartupStepResult.RecoverableError) {
        mutableState.value = StartupMigrationState.RecoverableError(
            kind = error.kind,
            diagnostic = error.diagnostic,
            actions = error.actions,
        )
    }
}
