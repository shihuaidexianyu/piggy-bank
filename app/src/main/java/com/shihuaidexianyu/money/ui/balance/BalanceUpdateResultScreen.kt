package com.shihuaidexianyu.money.ui.balance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceResult
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyInlineLabelValue
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun BalanceUpdateResultScreen(
    result: UpdateBalanceResult,
    settings: AppSettings,
    onDone: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MoneyFormPage(
        title = "更新结果",
        modifier = modifier,
    ) {
        item {
            MoneyCard {
                Text(result.accountName, style = MaterialTheme.typography.titleMedium)
                MoneyInlineLabelValue(
                    label = "更新前系统余额",
                    value = AmountFormatter.format(result.systemBalanceBeforeUpdate, settings),
                )
                MoneyInlineLabelValue(
                    label = "本次确认余额",
                    value = AmountFormatter.format(result.actualBalance, settings),
                )
                MoneyInlineLabelValue(
                    label = "差额",
                    value = AmountFormatter.format(result.delta, settings),
                )
                Text(
                    "本次已保存为一条余额更新记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            MoneyCard {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
                OutlinedButton(
                    onClick = { onOpenAccount(result.accountId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看账户详情")
                }
            }
        }
    }
}
