package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarHostState
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton

@Composable
fun CreateSavingsGoalScreen(
    viewModel: CreateSavingsGoalViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectUiEffects(
        effectFlow = viewModel.effectFlow,
        snackbarHostState = snackbarHostState,
        handler = { effect ->
            if (effect is CreateSavingsGoalEffect.Saved) {
                onBack()
            }
        },
    )

    MoneyFormPage(
        title = "新建储蓄目标",
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (!state.isLoading) {
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
                    label = "创建目标",
                )
            }
        }
    }
}
