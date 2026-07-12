package com.shihuaidexianyu.money.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R

enum class FormBackDecision {
    EXIT,
    CONFIRM_DISCARD,
}

fun resolveFormBack(isDirty: Boolean): FormBackDecision {
    return if (isDirty) FormBackDecision.CONFIRM_DISCARD else FormBackDecision.EXIT
}

@Composable
fun rememberDirtyFormBackAction(
    isDirty: Boolean,
    onExit: () -> Unit,
): () -> Unit {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        when (resolveFormBack(isDirty)) {
            FormBackDecision.EXIT -> onExit()
            FormBackDecision.CONFIRM_DISCARD -> showDiscardConfirmation = true
        }
    }
    BackHandler(onBack = requestBack)
    if (showDiscardConfirmation) {
        MoneyConfirmDialog(
            title = stringResource(R.string.form_discard_title),
            message = stringResource(R.string.form_discard_message),
            onConfirm = {
                showDiscardConfirmation = false
                onExit()
            },
            onDismiss = { showDiscardConfirmation = false },
            confirmLabel = stringResource(R.string.form_discard_action),
            dismissLabel = stringResource(R.string.form_continue_editing),
        )
    }
    return requestBack
}
