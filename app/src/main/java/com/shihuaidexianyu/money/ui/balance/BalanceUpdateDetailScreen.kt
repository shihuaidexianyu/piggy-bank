package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyConfirmDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BalanceUpdateDetailScreen(
    viewModel: BalanceUpdateDetailViewModel,
    state: BalanceUpdateDetailUiState,
    settings: AppSettings,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is BalanceUpdateDetailEffect.Deleted) onDeleted()
    }

    if (showDeleteConfirm) {
        MoneyConfirmDialog(
            title = "撤销余额更新",
            message = "撤销后会重新计算该账户当前余额和投资结算，确认继续？",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
            confirmLabel = "确认撤销",
            dismissLabel = "取消",
        )
    }

    MoneyFormPage(
        title = "余额更新详情",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item {
            MoneyCard {
                if (state.isLoading) {
                    Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = "时间",
                        value = DateTimeTextFormatter.format(state.occurredAt),
                    )
                    MoneyInlineLabelValue(
                        label = "更新前系统余额",
                        value = AmountFormatter.format(state.systemBalanceBeforeUpdate, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "本次确认余额",
                        value = AmountFormatter.format(state.actualBalance, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "差额",
                        value = AmountFormatter.format(state.delta, settings),
                    )
                }
            }
        }
        state.settlementSummary?.let { settlement ->
            item {
                MoneyCard {
                    Text("结算信息", style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = "本周期盈亏",
                        value = AmountFormatter.format(settlement.pnl, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "本周期收益率",
                        value = "${"%.2f".format(settlement.returnRate * 100)}%",
                    )
                    MoneyInlineLabelValue(
                        label = "本周期净转入",
                        value = AmountFormatter.format(settlement.netTransferIn, settings),
                    )
                    MoneyInlineLabelValue(
                        label = "本周期净转出",
                        value = AmountFormatter.format(settlement.netTransferOut, settings),
                    )
                }
            }
        }
        item {
            MoneyCard {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isDeleting,
                ) {
                    Text("修改记录")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isDeleting,
                ) {
                    Text(if (state.isDeleting) "撤销中..." else "撤销这次更新")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("返回")
                }
            }
        }
    }
}
