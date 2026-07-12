package com.shihuaidexianyu.money.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton

@Composable
fun SavingsGoalScreen(
    viewModel: SavingsGoalViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectUiEffects(
        effectFlow = viewModel.effectFlow,
        snackbarHostState = snackbarHostState,
        handler = { effect ->
            when (effect) {
                SavingsGoalEffect.Saved, SavingsGoalEffect.Cleared -> onBack()
                else -> {}
            }
        },
    )

    if (state.showClearConfirm) {
        MoneyConfirmDialog(
            title = stringResource(R.string.savings_goal_clear_title),
            message = stringResource(R.string.savings_goal_clear_message),
            onConfirm = viewModel::clear,
            onDismiss = viewModel::dismissClearConfirm,
            confirmLabel = stringResource(R.string.action_clear),
        )
    }

    MoneyFormPage(
        title = stringResource(
            if (state.hasGoal) R.string.savings_goal_edit_title else R.string.savings_goal_set_title,
        ),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "savings-goal"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        if (!state.isLoading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.savings_goal_amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyAmountField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        label = stringResource(R.string.savings_goal_amount),
                    )
                    Text(
                        text = stringResource(R.string.savings_goal_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    label = stringResource(
                        if (state.hasGoal) R.string.savings_goal_edit_action else R.string.savings_goal_set_action,
                    ),
                )
            }
            if (state.hasGoal) {
                item {
                    OutlinedButton(
                        onClick = viewModel::showClearConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.savings_goal_clear_action))
                    }
                }
            }
        }
    }
}
