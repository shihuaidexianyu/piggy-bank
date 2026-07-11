package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import kotlinx.coroutines.CancellationException

sealed interface RootActionExecutionResult {
    data object Success : RootActionExecutionResult
    data class PermanentFailure(val message: String) : RootActionExecutionResult
    data class RetryableFailure(val message: String) : RootActionExecutionResult
}

suspend fun executeRootSnackbarAction(
    action: RootSnackbarAction?,
    restoreLedger: suspend (com.shihuaidexianyu.money.domain.model.LedgerUndoToken) -> RestoreLedgerResult,
    undoReminderSkip: suspend (com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken) -> UndoReminderSkipResult,
    createAccount: () -> Unit,
    manageAccounts: () -> Unit,
): RootActionExecutionResult = try {
    when (action) {
        is RootSnackbarAction.RestoreLedger -> when (restoreLedger(action.undoToken)) {
            RestoreLedgerResult.RESTORED, RestoreLedgerResult.ALREADY_ACTIVE -> RootActionExecutionResult.Success
            else -> RootActionExecutionResult.PermanentFailure("记录已发生变化，无法撤销")
        }
        is RootSnackbarAction.UndoReminderSkip -> when (undoReminderSkip(action.undoToken)) {
            UndoReminderSkipResult.RESTORED, UndoReminderSkipResult.ALREADY_RESTORED -> RootActionExecutionResult.Success
            UndoReminderSkipResult.STALE -> RootActionExecutionResult.PermanentFailure("提醒已发生变化，无法撤销")
            UndoReminderSkipResult.NOT_FOUND -> RootActionExecutionResult.PermanentFailure("提醒已删除，无法撤销")
        }
        RootSnackbarAction.CreateAccount -> createAccount().let { RootActionExecutionResult.Success }
        RootSnackbarAction.ManageAccounts -> manageAccounts().let { RootActionExecutionResult.Success }
        null -> RootActionExecutionResult.Success
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    RootActionExecutionResult.RetryableFailure("操作失败，请稍后重试")
}
