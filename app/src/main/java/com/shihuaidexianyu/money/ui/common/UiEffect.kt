package com.shihuaidexianyu.money.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.SharedFlow

interface UiEffect {
    interface HasMessage {
        val message: String
    }
}

@Composable
fun <T> CollectUiEffects(
    effectFlow: SharedFlow<T>,
    snackbarHostState: SnackbarHostState,
    handler: (T) -> Unit,
) {
    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is UiEffect.HasMessage) {
                snackbarHostState.showSnackbar(effect.message)
            } else {
                handler(effect)
            }
        }
    }
}
