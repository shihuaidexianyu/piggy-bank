package com.shihuaidexianyu.money.ui.balance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.signedFormatInAppAmount
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

@Composable
fun BalanceUpdateResultScreen(
    result: UpdateBalanceResult,
    settings: PortableSettings,
    onDone: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDone)
    MoneyFormPage(
        title = stringResource(R.string.balance_result_title),
        modifier = modifier,
    ) {
        item {
            MoneyCard {
                Text(result.accountName, style = MaterialTheme.typography.titleMedium)
                MoneyInlineLabelValue(
                    label = stringResource(R.string.field_occurred_time),
                    value = DateTimeTextFormatter.format(result.occurredAt),
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_before_reconciliation),
                    value = formatInAppAmount(result.systemBalanceBeforeUpdate, settings),
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_confirmed),
                    value = formatInAppAmount(result.actualBalance, settings),
                )
                val moneyColors = LocalMoneyColors.current
                MoneyInlineLabelValue(
                    label = stringResource(
                        if (result.isInvestmentAccount) R.string.balance_delta_investment else R.string.balance_delta,
                    ),
                    value = signedFormatInAppAmount(result.delta, settings),
                    valueColor = when {
                        result.delta > 0L -> moneyColors.income
                        result.delta < 0L -> moneyColors.expense
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (result.isInvestmentAccount && result.delta != 0L) {
                    // Same gain/loss + percentage copy as the reconcile form's verdict card.
                    Text(
                        text = investmentDeltaText(result.delta, result.systemBalanceBeforeUpdate),
                        color = if (result.delta > 0L) moneyColors.income else moneyColors.expense,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    when {
                        result.delta == 0L -> stringResource(R.string.balance_result_reconciliation_saved)
                        result.isInvestmentAccount -> stringResource(R.string.balance_result_investment_saved)
                        else -> stringResource(R.string.balance_result_adjustment_saved)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            MoneyCard {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
                MoneyTonalButton(
                    onClick = { onOpenAccount(result.accountId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_view_account_detail))
                }
            }
        }
    }
}
