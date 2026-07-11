package com.shihuaidexianyu.money.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.usecase.MonthlyBudgetStatus
import com.shihuaidexianyu.money.ui.common.AccountPickerDialog
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyPageTitle
import com.shihuaidexianyu.money.ui.common.LocalRootSnackbarDispatcher
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors
import com.shihuaidexianyu.money.ui.common.formatInAppAmount

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarMessage: String? = null,
    onSnackbarMessageShown: () -> Unit = {},
    onStartUpdateBalance: (Long) -> Unit,
    onAllRemindersClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onManageAccounts: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenMonthlyBudgetEditor: () -> Unit = {},
    onDismissMonthlyBudgetEditor: () -> Unit = {},
    onMonthlyBudgetInputChange: (String) -> Unit = {},
    onSaveMonthlyBudget: () -> Unit = {},
    onRetryMonthlyBudgetSave: () -> Unit = {},
    onCloseMonthlyBudget: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showUpdateBalancePicker by remember { mutableStateOf(false) }
    val rootSnackbarDispatcher = LocalRootSnackbarDispatcher.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            rootSnackbarDispatcher?.dispatch(rootSnackbarEffect(it, token = "home:$it"))
            onSnackbarMessageShown()
        }
    }

    if (showUpdateBalancePicker) {
        AccountPickerDialog(
            title = "选择核对余额账户",
            accounts = state.accountOptions,
            onDismiss = { showUpdateBalancePicker = false },
            onPick = { accountId ->
                showUpdateBalancePicker = false
                onStartUpdateBalance(accountId)
            },
        )
    }
    if (state.showMonthlyBudgetEditor) {
        MonthlyBudgetEditorDialog(
            input = state.monthlyBudgetInput,
            inputError = state.monthlyBudgetInputError,
            saveError = state.monthlyBudgetSaveError,
            isSaving = state.isMonthlyBudgetSaving,
            hasBudget = state.monthlyBudget != null,
            onInputChange = onMonthlyBudgetInputChange,
            onSave = if (state.monthlyBudgetSaveError != null) {
                onRetryMonthlyBudgetSave
            } else {
                onSaveMonthlyBudget
            },
            onCloseBudget = onCloseMonthlyBudget,
            onDismiss = onDismissMonthlyBudgetEditor,
        )
    }
    Column(modifier = modifier) {
        MoneyPageTitle(
            title = "首页",
            trailing = {
                HomeHeaderActions(
                    dueCount = state.dueReminders.size + state.staleAccountCount,
                    onOpenSettings = onOpenSettings,
                    onOpenReminders = onAllRemindersClick,
                )
            },
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp),
        )
        AsyncContentRenderer(
            content = state.toAsyncContent(),
            onRetry = onRetry,
            modifier = Modifier
                .fillMaxSize(),
            empty = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MoneyEmptyStateCard(
                        title = "创建第一个账户",
                        subtitle = "创建账户后，就能开始记录收入、支出和转账。",
                    ) {
                        OutlinedButton(onClick = onCreateAccount) {
                            Text("立即创建")
                        }
                    }
                }
            },
            data = { renderedState, _ ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PeriodOverviewBlock(
                        totalAssets = renderedState.totalAssets,
                        cashInflow = renderedState.periodCashInflow,
                        cashOutflow = renderedState.periodCashOutflow,
                        settings = renderedState.settings,
                    )
                    if (renderedState.accountOptions.isEmpty()) {
                        HomeOpenAccountCta(onManageAccounts = onManageAccounts)
                    }
                    MonthlyBudgetBlock(
                        budget = renderedState.monthlyBudget,
                        settings = renderedState.settings,
                        onEdit = onOpenMonthlyBudgetEditor,
                        onClose = onCloseMonthlyBudget,
                    )
                    HomeReminderSection(
                        reminders = renderedState.dueReminders,
                        onOpenReminders = onAllRemindersClick,
                    )
                    HomeStaleAccountSection(
                        accounts = renderedState.staleAccounts,
                        settings = renderedState.settings,
                        onReconcile = onStartUpdateBalance,
                        onChooseAccount = { showUpdateBalancePicker = true },
                        showReconcileAction = renderedState.accountOptions.isNotEmpty(),
                    )
                }
            },
        )
    }
}
@Composable
private fun HomeOpenAccountCta(
    onManageAccounts: () -> Unit,
) {
    MoneyEmptyStateCard(
        title = "创建或重新开启可用账户",
        subtitle = "当前没有可记账的开放账户，历史净资产仍会保留。",
    ) {
        OutlinedButton(onClick = onManageAccounts) { Text("管理账户") }
    }
}

@Composable
fun HomeHeaderActions(
    dueCount: Int,
    onOpenSettings: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        ReminderHeaderButton(
            dueCount = dueCount,
            onClick = onOpenReminders,
        )
    }
}

@Composable
private fun ReminderHeaderButton(
    dueCount: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = CircleShape,
            ),
    ) {
        BadgedBox(
            badge = {
                if (dueCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = "提醒",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PeriodOverviewBlock(
    totalAssets: Long,
    cashInflow: Long,
    cashOutflow: Long,
    settings: PortableSettings,
) {
    val moneyColors = LocalMoneyColors.current
    val cashNet = cashInflow - cashOutflow
    val netColor = when {
        cashNet > 0 -> moneyColors.income
        cashNet < 0 -> moneyColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(16.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "当前净资产",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val recordText = formatInAppAmount(totalAssets, settings)
            val recordStyle = when {
                recordText.length > 12 -> MaterialTheme.typography.headlineSmall
                recordText.length > 8 -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.displayLarge
            }
            Text(
                text = recordText,
                style = recordStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PeriodMetricRow(
                    label = "本月收入",
                    value = formatInAppAmount(cashInflow, settings),
                    color = moneyColors.income,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
                PeriodMetricRow(
                    label = "本月支出",
                    value = formatInAppAmount(cashOutflow, settings),
                    color = moneyColors.expense,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
                PeriodMetricRow(
                    label = "本月净现金流",
                    value = formatInAppAmount(cashNet, settings),
                    color = netColor,
                )
            }
        }
    }
}

@Composable
private fun MonthlyBudgetBlock(
    budget: MonthlyBudgetStatus?,
    settings: PortableSettings,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    MoneySectionHeader(title = "月预算")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (budget == null) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("未设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onEdit) { Text("设置月预算") }
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "已支出 ${formatInAppAmount(budget.spentAmount, settings)} / " +
                            formatInAppAmount(budget.targetAmount, settings),
                    )
                    Text(budget.percentageText, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { budget.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (budget.overBudgetAmount != null && budget.overBudgetPercentageText != null) {
                    Text(
                        text = "超支 ${budget.overBudgetPercentageText} · " +
                            formatInAppAmount(budget.overBudgetAmount, settings),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text("修改月预算") }
                    TextButton(onClick = onClose) { Text("关闭月预算") }
                }
            }
        }
    }
}

@Composable
private fun HomeReminderSection(
    reminders: List<DueReminderUiModel>,
    onOpenReminders: () -> Unit,
) {
    MoneySectionHeader(title = "待处理提醒")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (reminders.isEmpty()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("暂无到期提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenReminders) { Text("管理提醒") }
            }
        } else {
            Column {
                reminders.forEachIndexed { index, reminder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenReminders)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(reminder.name)
                        Text(reminder.amountFormatted, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index != reminders.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeStaleAccountSection(
    accounts: List<StaleAccountUiModel>,
    settings: PortableSettings,
    onReconcile: (Long) -> Unit,
    onChooseAccount: () -> Unit,
    showReconcileAction: Boolean,
) {
    MoneySectionHeader(title = "待核对账户")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        if (accounts.isEmpty()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("暂无待核对账户", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showReconcileAction) {
                    TextButton(onClick = onChooseAccount) { Text("核对账户") }
                }
            }
        } else {
            Column {
                accounts.forEachIndexed { index, account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReconcile(account.accountId) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(account.name)
                        Text(
                            formatInAppAmount(account.currentBalance, settings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (index != accounts.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MonthlyBudgetEditorDialog(
    input: String,
    inputError: String?,
    saveError: String?,
    isSaving: Boolean,
    hasBudget: Boolean,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onCloseBudget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasBudget) "修改月预算" else "设置月预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isSaving,
                    label = { Text("每月支出预算") },
                    isError = inputError != null || saveError != null,
                    supportingText = {
                        (inputError ?: saveError)?.let { Text(it) }
                    },
                    singleLine = true,
                )
                if (hasBudget) {
                    TextButton(onClick = onCloseBudget, enabled = !isSaving) {
                        Text("关闭月预算")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                Text(if (saveError != null) "重试" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") }
        },
    )
}

@Composable
private fun PeriodMetricRow(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
