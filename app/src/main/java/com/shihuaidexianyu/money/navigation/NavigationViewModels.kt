package com.shihuaidexianyu.money.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
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

@Composable
internal fun rememberSettingsViewModel(
    container: MoneyAppContainer,
    key: String,
): SettingsViewModel {
    return viewModel(
        key = key,
        factory = moneyViewModelFactory {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
            )
        },
    )
}
