package com.shihuaidexianyu.money.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.SharedFlow

interface UiEffect {
    interface HasMessage {
        val message: String
    }
}

val LocalRootSnackbarDispatcher = staticCompositionLocalOf<RootSnackbarDispatcher?> { null }

fun Throwable.userMessage(fallback: String): String = message ?: fallback

@Composable
fun <T> CollectUiEffects(
    effectFlow: SharedFlow<T>,
    snackbarHostState: SnackbarHostState,
    handler: (T) -> Unit,
) {
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is UiEffect.HasMessage) {
                rootDispatcher?.dispatch(rootSnackbarEffect(effect.message))
                    ?: snackbarHostState.showSnackbar(effect.message)
            } else {
                handler(effect)
            }
        }
    }
}
