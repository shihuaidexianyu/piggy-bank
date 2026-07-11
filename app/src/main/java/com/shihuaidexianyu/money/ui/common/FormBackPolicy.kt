package com.shihuaidexianyu.money.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
            title = "放弃未保存的更改？",
            message = "当前草稿尚未保存。",
            onConfirm = {
                showDiscardConfirmation = false
                onExit()
            },
            onDismiss = { showDiscardConfirmation = false },
            confirmLabel = "放弃",
            dismissLabel = "继续编辑",
        )
    }
    return requestBack
}
