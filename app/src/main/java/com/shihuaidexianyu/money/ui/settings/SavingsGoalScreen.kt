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
import androidx.compose.ui.unit.dp
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

    if (state.showDeleteConfirm) {
        MoneyConfirmDialog(
            title = "删除储蓄目标",
            message = "确定要删除储蓄目标吗？此操作不可撤销。",
            onConfirm = viewModel::clear,
            onDismiss = viewModel::dismissDeleteConfirm,
            confirmLabel = "删除",
        )
    }

    MoneyFormPage(
        title = if (state.hasGoal) "编辑储蓄目标" else "设置储蓄目标",
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
                        text = "目标金额",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyAmountField(
                        value = state.amountText,
                        onValueChange = viewModel::updateAmount,
                        label = "目标金额",
                    )
                    Text(
                        text = "总资产达到此金额时即视为达成目标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MoneySaveButton(
                    onClick = viewModel::save,
                    isSaving = state.isSaving,
                    label = if (state.hasGoal) "保存修改" else "设置目标",
                )
            }
            if (state.hasGoal) {
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
}
