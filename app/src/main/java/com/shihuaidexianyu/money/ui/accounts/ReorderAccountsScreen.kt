package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AccountKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyTonalButton
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.AccountIconBadge
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol
import com.shihuaidexianyu.money.util.AmountFormatter

@Composable
fun ReorderAccountsScreen(
    viewModel: ReorderAccountsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is ReorderAccountsEffect.Saved) onBack()
    }

    MoneyFormPage(
        title = stringResource(R.string.accounts_order),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = guardedBack,
        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = MoneyDimens.bottomNavContentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.isLoading || state.loadErrorMessage != null) {
            item {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessage, "reorder-accounts"),
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        if (state.accounts.isEmpty()) {
            item {
                MoneyEmptyStateCard(
                    title = stringResource(R.string.accounts_none),
                    subtitle = stringResource(R.string.accounts_reorder_empty_description),
                )
            }
        } else {
            item {
                MoneyCard {
                    Text(
                        text = stringResource(R.string.accounts_reorder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MoneyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.sortMode == ReorderSortMode.BALANCE,
                                onClick = viewModel::sortByBalance,
                                label = { Text(stringResource(R.string.accounts_sort_balance)) },
                            )
                            FilterChip(
                                selected = state.sortMode == ReorderSortMode.RECENT,
                                onClick = viewModel::sortByRecentUse,
                                label = { Text(stringResource(R.string.accounts_sort_recent)) },
                            )
                            FilterChip(
                                selected = state.sortMode == ReorderSortMode.NAME,
                                onClick = viewModel::sortByName,
                                label = { Text(stringResource(R.string.accounts_sort_name)) },
                            )
                        }
                        if (state.sortMode != ReorderSortMode.NONE || state.isDirty) {
                            TextButton(onClick = viewModel::restoreDefaultOrder) {
                                Text(stringResource(R.string.accounts_restore_order))
                            }
                        }
                    }
                }
            }
            item {
                MoneySectionHeader(title = stringResource(R.string.accounts_order))
            }
            item {
                MoneyListSection {
                    state.accounts.forEachIndexed { index, account ->
                        MoneyListRow(
                            title = account.name,
                            subtitle = stringResource(
                                if (account.kind == AccountKind.INVESTMENT) {
                                    R.string.account_kind_investment
                                } else {
                                    R.string.account_kind_funding
                                },
                            ) + " · " + stringResource(
                                R.string.account_picker_balance_format,
                                "${LocalCurrencySymbol.current}${AmountFormatter.formatPlain(account.balance)}",
                            ) + if (account.isHidden) {
                                " · " + stringResource(R.string.accounts_hidden_short)
                            } else {
                                ""
                            },
                            showChevron = false,
                            leading = {
                                AccountIconBadge(
                                    iconName = account.iconName,
                                    colorName = account.colorName,
                                    size = 30.dp,
                                    iconSize = 17.dp,
                                )
                            },
                            accessory = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MoneyTonalButton(
                                        onClick = { viewModel.moveAccountUp(account.id) },
                                        enabled = index > 0,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(stringResource(R.string.action_move_up))
                                    }
                                    MoneyTonalButton(
                                        onClick = { viewModel.moveAccountDown(account.id) },
                                        enabled = index < state.accounts.lastIndex,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(stringResource(R.string.action_move_down))
                                    }
                                }
                            },
                        )
                        if (index != state.accounts.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
            item {
                MoneyCard {
                    MoneySaveButton(
                        onClick = viewModel::save,
                        isSaving = state.isSaving,
                        label = stringResource(R.string.accounts_save_order),
                    )
                }
            }
        }
    }
}
