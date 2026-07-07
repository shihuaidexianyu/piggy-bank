package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.MoneyAmountField
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneySingleLineField
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun SavingsGoalFormFields(
    name: String,
    amountText: String,
    accounts: List<SavingsGoalAccountUiModel>,
    settings: AppSettings,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onToggleAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "目标名称",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneySingleLineField(
            value = name,
            onValueChange = onNameChange,
            label = null,
            placeholder = "如：应急储蓄、旅行基金",
        )

        Text(
            text = "目标金额",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyAmountField(
            value = amountText,
            onValueChange = onAmountChange,
            label = "目标金额",
        )

        MoneySectionHeader(title = "关联账户", trailing = "${accounts.count { it.isSelected }} 个")
        if (accounts.isEmpty()) {
            Text(
                text = "暂无可用账户",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MoneyCard(contentPadding = PaddingValues(0.dp)) {
                Column {
                    accounts.forEachIndexed { index, account ->
                        SavingsGoalAccountRow(
                            account = account,
                            balanceText = AmountFormatter.format(account.balance, settings),
                            onClick = { onToggleAccount(account.id) },
                        )
                        if (index < accounts.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsGoalAccountRow(
    account: SavingsGoalAccountUiModel,
    balanceText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIconBadge(
            iconName = account.iconName,
            colorName = account.colorName,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = balanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (account.isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (account.isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
