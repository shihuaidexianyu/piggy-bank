package com.shihuaidexianyu.money.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.MoneyChoiceDialog
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.MoneyTextInputDialog

private sealed interface SettingsDialog {
    data object HomePeriod : SettingsDialog
    data object ThemeMode : SettingsDialog
    data object AmountColorMode : SettingsDialog
    data object CurrencySymbol : SettingsDialog
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onHomePeriodChange: (HomePeriod) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmountColorModeChange: (AmountColorMode) -> Unit,
    onCurrencySymbolChange: (String) -> Unit,
    onShowStaleMarkChange: (Boolean) -> Unit,
    onManageAccountOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var currencyDraft by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }

    dialog?.let { currentDialog ->
        when (currentDialog) {
            SettingsDialog.HomePeriod -> {
                MoneyChoiceDialog(
                    title = "首页默认周期",
                    options = HomePeriod.entries,
                    selected = settings.homePeriod,
                    label = { it.displayName },
                    onSelect = {
                        onHomePeriodChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.ThemeMode -> {
                MoneyChoiceDialog(
                    title = "主题模式",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.displayName },
                    onSelect = {
                        onThemeModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.AmountColorMode -> {
                MoneyChoiceDialog(
                    title = "金额颜色习惯",
                    options = AmountColorMode.entries,
                    selected = settings.amountColorMode,
                    label = { it.displayName },
                    onSelect = {
                        onAmountColorModeChange(it)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            }

            SettingsDialog.CurrencySymbol -> {
                MoneyTextInputDialog(
                    title = "货币符号",
                    value = currencyDraft,
                    onValueChange = { currencyDraft = it },
                    onConfirm = {
                        onCurrencySymbolChange(currencyDraft)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                    confirmLabel = "保存",
                )
            }
        }
    }

    MoneyFormPage(
        title = "设置",
        modifier = modifier,
    ) {
        item { MoneySectionHeader(title = "显示") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "首页默认周期",
                    subtitle = "控制首页净流入和净流出的默认周期",
                    trailing = settings.homePeriod.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.HomePeriod },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "主题模式",
                    subtitle = "跟随系统，或固定为浅色 / 深色",
                    trailing = settings.themeMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.ThemeMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "金额颜色习惯",
                    subtitle = "统一首页、历史和金额差额的红绿显示",
                    trailing = settings.amountColorMode.displayName,
                    modifier = Modifier.clickable { dialog = SettingsDialog.AmountColorMode },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "货币符号",
                    subtitle = "影响金额显示格式",
                    trailing = settings.currencySymbol,
                    modifier = Modifier.clickable {
                        currencyDraft = settings.currencySymbol
                        dialog = SettingsDialog.CurrencySymbol
                    },
                )
                MoneySectionDivider()
                MoneyListRow(
                    title = "显示过期账户标记",
                    subtitle = "在首页和账户页提醒哪些余额需要更新",
                    showChevron = false,
                    accessory = {
                        Switch(
                            checked = settings.showStaleMark,
                            onCheckedChange = onShowStaleMarkChange,
                        )
                    },
                )
            }
        }

        item { MoneySectionHeader(title = "账户管理") }
        item {
            MoneyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                MoneyListRow(
                    title = "账户顺序",
                    subtitle = "调整账户页和选择器里的展示顺序",
                    trailing = "自定义",
                    modifier = Modifier.clickable(onClick = onManageAccountOrder),
                )
            }
        }


    }
}
