package com.shihuaidexianyu.money.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import java.io.Serializable
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RootSnackbarAction : Serializable {
    data class RestoreLedger(val undoToken: LedgerUndoToken) : RootSnackbarAction
    data class UndoReminderSkip(val undoToken: ReminderSkipUndoToken) : RootSnackbarAction
    data object CreateAccount : RootSnackbarAction
    data object ManageAccounts : RootSnackbarAction
}

data class RootSnackbarEffect(
    val token: String,
    val message: String,
    val actionLabel: String? = null,
    val action: RootSnackbarAction? = null,
) : Serializable

fun rootSnackbarEffect(
    message: String,
    actionLabel: String? = null,
    action: RootSnackbarAction? = null,
    token: String = UUID.randomUUID().toString(),
) = RootSnackbarEffect(token, message, actionLabel, action)

fun interface RootSnackbarDispatcher {
    fun dispatch(effect: RootSnackbarEffect)
}

class RootSnackbarQueueViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _queue = MutableStateFlow(
        savedStateHandle.get<ArrayList<RootSnackbarEffect>>(QUEUE_KEY)?.toList().orEmpty(),
    )
    val queue: StateFlow<List<RootSnackbarEffect>> = _queue.asStateFlow()

    val current: RootSnackbarEffect?
        get() = _queue.value.firstOrNull()

    fun enqueue(
        message: String,
        actionLabel: String? = null,
        action: RootSnackbarAction? = null,
    ): String {
        val effect = RootSnackbarEffect(
            token = UUID.randomUUID().toString(),
            message = message,
            actionLabel = actionLabel,
            action = action,
        )
        persist(_queue.value + effect)
        return effect.token
    }

    fun enqueue(effect: RootSnackbarEffect) {
        if (_queue.value.any { it.token == effect.token }) return
        persist(_queue.value + effect)
    }

    fun ack(token: String): Boolean {
        val current = current ?: return false
        if (current.token != token) return false
        persist(_queue.value.drop(1))
        return true
    }

    fun replaceHead(token: String, replacement: RootSnackbarEffect): Boolean {
        val current = current ?: return false
        if (current.token != token) return false
        persist(listOf(replacement) + _queue.value.drop(1))
        return true
    }

    private fun persist(next: List<RootSnackbarEffect>) {
        _queue.value = next
        savedStateHandle[QUEUE_KEY] = ArrayList(next)
    }

    private companion object {
        const val QUEUE_KEY = "root_snackbar_queue"
    }
}
