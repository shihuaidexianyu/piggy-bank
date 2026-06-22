package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.theme.MoneyTheme

@Preview(name = "MoneyCard - light", showBackground = true)
@Preview(name = "MoneyCard - dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoneyCardPreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT, amountColorMode = AmountColorMode.RED_INCOME_GREEN_EXPENSE) {
        Box(modifier = Modifier.padding(16.dp)) {
            MoneyCard {
                Text("账户余额", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("¥ 12,345.67")
                Text("本周期收入 ¥ 8,000 / 支出 ¥ 3,200")
            }
        }
    }
}

@Preview(name = "MoneyListRow - default", showBackground = true)
@Preview(name = "MoneyListRow - with subtitle & accessory", showBackground = true)
@Composable
private fun MoneyListRowPreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT) {
        Column(modifier = Modifier.padding(16.dp)) {
            MoneyListRow(
                title = "招商银行",
                subtitle = "最近核对 2024-01-15",
                trailing = "¥ 12,345.67",
                showChevron = true,
            )
            MoneyListSection {
                MoneyListRow(
                    title = "现金",
                    subtitle = "尚未核对",
                    leading = {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                        )
                    },
                    accessory = { Text("待核对") },
                    showChevron = false,
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "微信零钱",
                    subtitle = "余额正常",
                    trailing = "¥ 543.20",
                )
            }
        }
    }
}

@Preview(name = "MoneyEmptyStateCard", showBackground = true)
@Composable
private fun MoneyEmptyStateCardPreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(16.dp)) {
            MoneyEmptyStateCard(
                title = "还没有账户",
                subtitle = "创建账户后开始记账。",
            )
        }
    }
}

@Preview(name = "MoneyStatusPill", showBackground = true)
@Composable
private fun MoneyStatusPillPreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            MoneyStatusPill(text = "待核对 3 个", accent = androidx.compose.material3.MaterialTheme.colorScheme.secondary)
            MoneyStatusPill(text = "余额正常", accent = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(name = "MoneySectionHeader", showBackground = true)
@Composable
private fun MoneySectionHeaderPreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(16.dp)) {
            MoneySectionHeader(title = "活跃账户", trailing = "3 个")
        }
    }
}

@Preview(name = "MoneyMetricTile", showBackground = true)
@Composable
private fun MoneyMetricTilePreview() {
    MoneyTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.padding(16.dp)) {
            MoneyMetricTile(label = "本期收入", value = "¥ 8,000")
        }
    }
}
