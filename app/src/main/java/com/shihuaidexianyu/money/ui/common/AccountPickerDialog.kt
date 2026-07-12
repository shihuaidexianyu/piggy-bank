package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.R

data class AccountOptionUiModel(
    val id: Long,
    val name: String,
    val colorName: String = "blue",
    val iconName: String = "wallet",
    val balance: Long? = null,
    val lastUsedAt: Long? = null,
    val isStale: Boolean = false,
    val isHidden: Boolean = false,
)

data class AccountPickerSections(
    val visibleAccounts: List<AccountOptionUiModel>,
    val hiddenAccounts: List<AccountOptionUiModel>,
    val hiddenAccountCount: Int,
    val hiddenExpanded: Boolean,
)

fun accountPickerSections(
    accounts: List<AccountOptionUiModel>,
    hiddenExpanded: Boolean,
): AccountPickerSections {
    val visibleAccounts = accounts.filterNot(AccountOptionUiModel::isHidden)
    val allHiddenAccounts = accounts.filter(AccountOptionUiModel::isHidden)
    return AccountPickerSections(
        visibleAccounts = visibleAccounts,
        hiddenAccounts = if (hiddenExpanded) allHiddenAccounts else emptyList(),
        hiddenAccountCount = allHiddenAccounts.size,
        hiddenExpanded = hiddenExpanded && allHiddenAccounts.isNotEmpty(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerDialog(
    title: String,
    accounts: List<AccountOptionUiModel>,
    selectedAccountId: Long? = null,
    disabledAccountIds: Set<Long> = emptySet(),
    settings: PortableSettings = PortableSettings(),
    noSelectionLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClearSelection: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AccountPickerList(
            title = title,
            accounts = accounts,
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
    settings: PortableSettings,
    noSelectionLabel: String?,
    onClearSelection: (() -> Unit)?,
    onPick: (Long) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }
    val sections = accountPickerSections(accounts, hiddenExpanded)
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
                        isClickable = true,
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

        if (sections.visibleAccounts.isNotEmpty()) {
            item {
                AccountPickerSection(
                    accounts = sections.visibleAccounts,
                    selectedAccountId = selectedAccountId,
                    disabledAccountIds = disabledAccountIds,
                    settings = settings,
                    onPick = onPick,
                )
            }
        }

        if (sections.hiddenAccountCount > 0) {
            item {
                MoneyListSection {
                    MoneyListRow(
                        title = if (sections.hiddenExpanded) {
                            stringResource(R.string.account_picker_collapse_hidden)
                        } else {
                            stringResource(R.string.account_picker_show_hidden_format, sections.hiddenAccountCount)
                        },
                        showChevron = false,
                        isClickable = true,
                        modifier = Modifier.clickable { hiddenExpanded = !sections.hiddenExpanded },
                        accessory = {
                            Icon(
                                imageVector = if (sections.hiddenExpanded) {
                                    Icons.Rounded.ExpandLess
                                } else {
                                    Icons.Rounded.ExpandMore
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        if (sections.hiddenExpanded) {
            item {
                Text(stringResource(R.string.accounts_hidden), style = MaterialTheme.typography.titleMedium)
            }
            item {
                AccountPickerSection(
                    accounts = sections.hiddenAccounts,
                    selectedAccountId = selectedAccountId,
                    disabledAccountIds = disabledAccountIds,
                    settings = settings,
                    onPick = onPick,
                )
            }
        }
    }
}

@Composable
private fun AccountPickerSection(
    accounts: List<AccountOptionUiModel>,
    selectedAccountId: Long?,
    disabledAccountIds: Set<Long>,
    settings: PortableSettings,
    onPick: (Long) -> Unit,
) {
    MoneyListSection {
        accounts.forEachIndexed { index, account ->
            val isDisabled = account.id in disabledAccountIds
            MoneyListRow(
                title = account.name,
                subtitle = account.pickerSubtitle(settings),
                showChevron = false,
                leading = {
                    AccountIconBadge(
                        iconName = account.iconName,
                        colorName = account.colorName,
                        size = 30.dp,
                        iconSize = 17.dp,
                    )
                },
                modifier = Modifier
                    .alpha(if (isDisabled) 0.45f else 1f)
                    .clickable(enabled = !isDisabled) { onPick(account.id) },
                accessory = {
                    when {
                        selectedAccountId == account.id -> {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.account_picker_selected_format, account.name),
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

@Composable
private fun AccountOptionUiModel.pickerSubtitle(
    settings: PortableSettings,
): String? {
    return balance?.let {
        stringResource(R.string.account_picker_balance_format, formatInAppAmount(it, settings))
    }
}

