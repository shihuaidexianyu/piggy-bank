package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.util.AmountFormatter

enum class AccountPickerSortMode {
    DEFAULT,
    MOST_USED,
    HIGHEST_BALANCE,
    STALE_FIRST,
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
    settings: AppSettings = AppSettings(),
    noSelectionLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClearSelection: (() -> Unit)? = null,
) {
    val sortedAccounts = accounts.sortedForPicker(sortMode)

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
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        )
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
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.titleLarge)
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
                        subtitle = account.pickerSubtitle(settings),
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
): String? {
    return balance?.let { "余额 ${AmountFormatter.format(it, settings)}" }
}

