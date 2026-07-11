package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import java.io.Serializable
import java.util.UUID

enum class FormTerminalKind {
    SAVED,
    DELETED,
}

data class PendingFormTerminal(
    val token: String,
    val kind: FormTerminalKind,
    val count: Int? = null,
    val balanceResult: UpdateBalanceResult? = null,
    val ledgerUndoToken: LedgerUndoToken? = null,
) : Serializable

fun pendingFormTerminal(
    kind: FormTerminalKind,
    count: Int? = null,
    balanceResult: UpdateBalanceResult? = null,
    ledgerUndoToken: LedgerUndoToken? = null,
): PendingFormTerminal = PendingFormTerminal(
    token = UUID.randomUUID().toString(),
    kind = kind,
    count = count,
    balanceResult = balanceResult,
    ledgerUndoToken = ledgerUndoToken,
)

const val PENDING_FORM_TERMINAL_KEY = "pending_form_terminal"
