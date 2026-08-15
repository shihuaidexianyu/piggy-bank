package com.shihuaidexianyu.money.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
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
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.home.netWorthGoalProgressPresentation
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun SavingsGoalScreen(
    viewModel: SavingsGoalViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootDispatcher = LocalRootSnackbarDispatcher.current
    val savedFeedbackMessage = stringResource(R.string.savings_goal_saved_feedback)
    val clearedFeedbackMessage = stringResource(R.string.savings_goal_cleared_feedback)

    CollectUiEffects(
        effectFlow = viewModel.effectFlow,
        snackbarHostState = snackbarHostState,
        handler = { effect ->
            when (effect) {
                SavingsGoalEffect.Saved -> {
                    rootDispatcher?.dispatch(rootSnackbarEffect(savedFeedbackMessage))
                    onBack()
                }
                SavingsGoalEffect.Cleared -> {
                    rootDispatcher?.dispatch(rootSnackbarEffect(clearedFeedbackMessage))
                    onBack()
                }
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
            destructive = true,
        )
    }

    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)

    MoneyFormPage(
        title = stringResource(
            if (state.hasGoal) R.string.savings_goal_edit_title else R.string.savings_goal_set_title,
        ),
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "savings-goal"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        if (!state.isLoading) {
            item {
                // One card, one label: the field's own floating label names the amount — the
                // former standalone caption duplicated it word for word.
                MoneyCard {
                    MoneyAmountField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        label = stringResource(R.string.savings_goal_amount),
                    )
                    state.progress?.let { progress ->
                        val symbol = LocalCurrencySymbol.current
                        val presentation = netWorthGoalProgressPresentation(
                            currentAmount = progress.currentAmount,
                            targetAmount = progress.targetAmount,
                        )
                        MoneyInlineLabelValue(
                            label = stringResource(R.string.savings_goal_current_net_worth),
                            value = stringResource(
                                R.string.savings_goal_progress_format,
                                "${symbol}${AmountFormatter.formatPlain(progress.currentAmount)}",
                                presentation.percentageText,
                            ),
                        )
                    }
                    Text(
                        text = stringResource(R.string.savings_goal_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneySaveButton(
                        onClick = viewModel::save,
                        isSaving = state.isSaving,
                        label = stringResource(
                            if (state.hasGoal) R.string.savings_goal_edit_action else R.string.savings_goal_set_action,
                        ),
                    )
                }
            }
            if (state.hasGoal) {
                item {
                    MoneyTonalButton(
                        onClick = viewModel::showClearConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving,
                    ) {
                        Text(stringResource(R.string.savings_goal_clear_action))
                    }
                }
            }
        }
    }
}
