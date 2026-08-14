package com.shihuaidexianyu.money.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.SharedFlow

interface UiEffect {
    interface HasMessage {
        val message: String

        /** Resource fallback used when [message] is blank; resolved by [resolveMessage]. */
        val messageRes: Int? get() = null
    }
}

fun UiEffect.HasMessage.resolveMessage(context: Context): String =
    message.takeIf { it.isNotBlank() }
        ?: messageRes?.let(context::getString)
        ?: message

val LocalRootSnackbarDispatcher = staticCompositionLocalOf<RootSnackbarDispatcher?> { null }

fun Throwable.userMessage(fallback: String): String = message ?: fallback

@Composable
fun <T> CollectUiEffects(
    effectFlow: SharedFlow<T>,
    snackbarHostState: SnackbarHostState,
    handler: (T) -> Unit,
) {
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    val context = LocalContext.current
    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            if (effect is UiEffect.HasMessage) {
                val text = effect.resolveMessage(context)
                rootDispatcher?.dispatch(rootSnackbarEffect(text))
                    ?: snackbarHostState.showSnackbar(text)
            } else {
                handler(effect)
            }
        }
    }
}
