package com.shihuaidexianyu.money.ui.balance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.ui.common.formatInAppAmount

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
                    label = stringResource(R.string.balance_before_reconciliation),
                    value = formatInAppAmount(result.systemBalanceBeforeUpdate, settings),
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_confirmed),
                    value = formatInAppAmount(result.actualBalance, settings),
                )
                MoneyInlineLabelValue(
                    label = stringResource(R.string.balance_delta),
                    value = formatInAppAmount(result.delta, settings),
                )
                Text(
                    if (result.delta == 0L) {
                        stringResource(R.string.balance_result_reconciliation_saved)
                    } else {
                        stringResource(R.string.balance_result_adjustment_saved)
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
                OutlinedButton(
                    onClick = { onOpenAccount(result.accountId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_view_account_detail))
                }
            }
        }
    }
}
