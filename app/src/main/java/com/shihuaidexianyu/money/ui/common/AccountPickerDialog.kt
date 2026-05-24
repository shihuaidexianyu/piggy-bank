package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

enum class AccountPickerSortMode {
    DEFAULT,
    MOST_USED,
    HIGHEST_BALANCE,
    STALE_FIRST,
}

enum class AccountPickerPresentation {
    BOTTOM_SHEET,
    FULL_SCREEN,
}

data class AccountOptionUiModel(
    val id: Long,
    val name: String,
    val colorName: String = "blue",
    val balance: Long? = null,
    val lastUsedAt: Long? = null,
    val isStale: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerDialog(
    title: String,
    accounts: List<AccountOptionUiModel>,
    selectedAccountId: Long? = null,
    disabledAccountIds: Set<Long> = emptySet(),
    sortMode: AccountPickerSortMode = AccountPickerSortMode.DEFAULT,
    presentation: AccountPickerPresentation = AccountPickerPresentation.BOTTOM_SHEET,
    settings: AppSettings = AppSettings(),
    noSelectionLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClearSelection: (() -> Unit)? = null,
) {
    val sortedAccounts = accounts.sortedForPicker(sortMode)

    when (presentation) {
        AccountPickerPresentation.BOTTOM_SHEET -> {
            ModalBottomSheet(onDismissRequest = onDismiss) {
                AccountPickerList(
                    title = title,
                    accounts = sortedAccounts,
                    selectedAccountId = selectedAccountId,
                    disabledAccountIds = disabledAccountIds,
                    settings = settings,
                    noSelectionLabel = noSelectionLabel,
                    onClearSelection = onClearSelection,
                    onPick = onPick,
                    showInlineTitle = true,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                )
            }
        }

        AccountPickerPresentation.FULL_SCREEN -> {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MoneyPageTitle(
                            title = title,
                            leading = { MoneyBackButton(onClick = onDismiss) },
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 4.dp),
                        )
                        AccountPickerList(
                            title = title,
                            accounts = sortedAccounts,
                            selectedAccountId = selectedAccountId,
                            disabledAccountIds = disabledAccountIds,
                            settings = settings,
                            noSelectionLabel = noSelectionLabel,
                            onClearSelection = onClearSelection,
                            onPick = onPick,
                            showInlineTitle = false,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPickerList(
    title: String,
    accounts: List<AccountOptionUiModel>,
    selectedAccountId: Long?,
    disabledAccountIds: Set<Long>,
    settings: AppSettings,
    noSelectionLabel: String?,
    onClearSelection: (() -> Unit)?,
    onPick: (Long) -> Unit,
    showInlineTitle: Boolean,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showInlineTitle) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }

        if (noSelectionLabel != null && onClearSelection != null) {
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = noSelectionLabel,
                        showChevron = false,
                        modifier = Modifier.clickable { onClearSelection() },
                        accessory = {
                            if (selectedAccountId == null) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }

        item {
            MoneyListSection {
                accounts.forEachIndexed { index, account ->
                    val isDisabled = account.id in disabledAccountIds
                    MoneyListRow(
                        title = account.name,
                        subtitle = account.pickerSubtitle(
                            settings = settings,
                            isDisabled = isDisabled,
                        ),
                        showChevron = false,
                        modifier = Modifier
                            .alpha(if (isDisabled) 0.45f else 1f)
                            .clickable(enabled = !isDisabled) { onPick(account.id) },
                        accessory = {
                            when {
                                selectedAccountId == account.id -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                isDisabled -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                    if (index != accounts.lastIndex) {
                        MoneySectionDivider()
                    }
                }
            }
        }
    }
}

private fun List<AccountOptionUiModel>.sortedForPicker(
    sortMode: AccountPickerSortMode,
): List<AccountOptionUiModel> {
    return when (sortMode) {
        AccountPickerSortMode.DEFAULT -> this
        AccountPickerSortMode.MOST_USED -> sortedWith(
            compareByDescending<AccountOptionUiModel> { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
        AccountPickerSortMode.HIGHEST_BALANCE -> sortedWith(
            compareByDescending<AccountOptionUiModel> { it.balance ?: Long.MIN_VALUE }
                .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
        AccountPickerSortMode.STALE_FIRST -> sortedWith(
            compareByDescending<AccountOptionUiModel> { it.isStale }
                .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )
    }
}

private fun AccountOptionUiModel.pickerSubtitle(
    settings: AppSettings,
    isDisabled: Boolean,
): String? {
    if (isDisabled) return "当前场景不可选"
    val parts = buildList {
        if (isStale) add("待核对")
        balance?.let { add("余额 ${AmountFormatter.format(it, settings)}") }
        lastUsedAt?.let { add("最近使用 ${DateTimeTextFormatter.format(it)}") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

