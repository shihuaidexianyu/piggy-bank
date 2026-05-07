package com.shihuaidexianyu.money.ui.balance

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BalanceAdjustmentDetailScreen(
    viewModel: BalanceAdjustmentDetailViewModel,
    state: BalanceAdjustmentDetailUiState,
    settings: AppSettings,
    onClosed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                BalanceAdjustmentDetailEffect.Closed -> onClosed()
            }
        }
    }

    MoneyFormPage(
        title = "余额矫正详情",
        modifier = modifier,
        onBack = onBack,
    ) {
        if (state.isLoading) {
            item {
                MoneyEmptyStateCard(
                    title = "加载中",
                    subtitle = "正在读取这条余额矫正记录。",
                )
            }
        } else {
            item {
                MoneyCard {
                    Text(state.accountName, style = MaterialTheme.typography.titleMedium)
                    MoneyInlineLabelValue(
                        label = "时间",
                        value = DateTimeTextFormatter.format(state.occurredAt),
                    )
                    MoneyInlineLabelValue(
                        label = "矫正差额",
                        value = AmountFormatter.format(state.delta, settings),
                    )
                }
            }
        }
    }
}
