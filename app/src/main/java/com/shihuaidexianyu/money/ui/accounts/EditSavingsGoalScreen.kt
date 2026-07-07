package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton

@Composable
fun EditSavingsGoalScreen(
    viewModel: EditSavingsGoalViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState.value
    val snackbarHostState = remember { SnackbarHostState() }

    CollectUiEffects(
        effectFlow = viewModel.effectFlow,
        snackbarHostState = snackbarHostState,
        handler = { effect ->
            when (effect) {
                EditSavingsGoalEffect.Saved, EditSavingsGoalEffect.Deleted, EditSavingsGoalEffect.Closed -> onBack()
                else -> {}
            }
        },
    )

    if (state.showDeleteConfirm) {
        MoneyConfirmDialog(
            title = "删除储蓄目标",
            message = "确定要删除「${state.name}」吗？此操作不可撤销。",
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = "删除",
        )
    }

    MoneyFormPage(
        title = "编辑储蓄目标",
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (!state.isLoading && !state.isMissing) {
            item {
                SavingsGoalFormFields(
                    name = state.name,
                    amountText = state.amountText,
                    accounts = state.accounts,
                    settings = com.shihuaidexianyu.money.domain.model.AppSettings(),
                    onNameChange = viewModel::updateName,
                    onAmountChange = viewModel::updateAmount,
                    onToggleAccount = viewModel::toggleAccount,
                )
            }
            item {
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    label = "保存修改",
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::showDeleteConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    enabled = !state.isSaving,
                ) {
                    Text("删除目标")
                }
            }
        }
    }
}
