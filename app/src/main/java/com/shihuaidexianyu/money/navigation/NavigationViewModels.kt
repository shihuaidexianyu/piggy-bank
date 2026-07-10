package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.ui.settings.SettingsViewModel

internal inline fun <reified VM : ViewModel> moneyViewModelFactory(
    crossinline create: () -> VM,
) = viewModelFactory {
    initializer { create() }
}

internal inline fun <reified VM : ViewModel> moneySavedStateViewModelFactory(
    crossinline create: (SavedStateHandle) -> VM,
) = viewModelFactory {
    initializer { create(createSavedStateHandle()) }
}

/**
 * Returns a single activity-scoped [SettingsViewModel] shared across all destinations.
 * Previously each balance/record screen created its own instance (6 total), each independently
 * observing the same DataStore — wasteful and inconsistent. Now all callers share one.
 */
@Composable
internal fun rememberSettingsViewModel(container: MoneyAppContainer): SettingsViewModel {
    val context = LocalContext.current
    return viewModel(
        // Scope to the activity so every destination shares the same instance.
        viewModelStoreOwner = context as? androidx.activity.ComponentActivity
            ?: error("SettingsViewModel requires a ComponentActivity context"),
        factory = moneyViewModelFactory {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                buildExportJsonUseCase = container.buildExportJsonUseCase,
                exportJsonFileWriter = container.exportJsonFileWriter,
                backupFileReader = container.backupFileReader,
                preImportBackupWriter = container.preImportBackupWriter,
                importBackupUseCase = container.importBackupUseCase,
                validateBackupSnapshotUseCase = container.validateBackupSnapshotUseCase,
            )
        },
    )
}

