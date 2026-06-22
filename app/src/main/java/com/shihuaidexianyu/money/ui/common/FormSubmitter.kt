package com.shihuaidexianyu.money.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared form-submit boilerplate. Most form ViewModels in this app follow the same pattern:
 * validate inputs synchronously (throwing [IllegalArgumentException] with a Chinese message),
 * flip `isSaving = true` on the UI state, run the use case, then either emit `Saved` or show a
 * snackbar with the failure message. [runFormSubmit] centralizes that flow so individual VMs
 * only need to provide the state update and the suspend mutation.
 *
 * Convention: validation failures (pre-`useCase` throws) are emitted via [tryEmit] on the
 * effect flow (synchronous, no coroutine needed), while use-case failures are emitted via
 * [emit] inside the launched coroutine. Both use the same [messageOf] fallback.
 */
suspend fun <T> runFormSubmit(
    effects: MutableSharedFlow<T>,
    savingState: Boolean,
    setSaving: (Boolean) -> Unit,
    useCase: suspend () -> Unit,
    onSuccess: () -> T,
    onFailure: (String) -> T,
) {
    if (savingState) return
    setSaving(true)
    try {
        useCase()
        effects.emit(onSuccess())
    } catch (e: Exception) {
        setSaving(false)
        effects.emit(onFailure(e.message ?: "操作失败"))
    }
}
