package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.common.MoneyCard
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.MoneyListRow
import com.shihuaidexianyu.money.ui.common.MoneyListSection
import com.shihuaidexianyu.money.ui.common.MoneySaveButton
import com.shihuaidexianyu.money.ui.common.MoneySectionDivider
import com.shihuaidexianyu.money.ui.common.MoneySectionHeader

@Composable
fun ReorderAccountsScreen(
    viewModel: ReorderAccountsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectUiEffects(viewModel.effectFlow, snackbarHostState) { effect ->
        if (effect is ReorderAccountsEffect.Saved) onBack()
    }

    MoneyFormPage(
        title = "账户顺序",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.groupOrder.all { state.accountsByGroup[it].isNullOrEmpty() }) {
            item {
                MoneyEmptyStateCard(
                    title = "还没有账户",
                    subtitle = "创建账户后才能调整顺序。",
                )
            }
        } else {
            item {
                MoneySectionHeader(title = "分类顺序")
            }
            item {
                MoneyListSection {
                    state.groupOrder.forEachIndexed { index, groupType ->
                        MoneyListRow(
                            title = groupType.displayName,
                            showChevron = false,
                            accessory = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.moveGroupUp(groupType) },
                                        enabled = index > 0,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text("上移")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.moveGroupDown(groupType) },
                                        enabled = index < state.groupOrder.lastIndex,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text("下移")
                                    }
                                }
                            },
                        )
                        if (index != state.groupOrder.lastIndex) {
                            MoneySectionDivider()
                        }
                    }
                }
            }
            state.groupOrder.forEach { groupType ->
                val accounts = state.accountsByGroup[groupType].orEmpty()
                if (accounts.isEmpty()) return@forEach
                item {
                    MoneySectionHeader(title = "${groupType.displayName}内顺序")
                }
                item {
                    MoneyListSection {
                        accounts.forEachIndexed { index, account ->
                            MoneyListRow(
                                title = account.name,
                                showChevron = false,
                                accessory = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.moveAccountUp(groupType, account.id) },
                                            enabled = index > 0,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text("上移")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.moveAccountDown(groupType, account.id) },
                                            enabled = index < accounts.lastIndex,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text("下移")
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
            item {
                MoneyCard {
                    MoneySaveButton(onClick = viewModel::save, isSaving = state.isSaving, label = "保存顺序")
                }
            }
        }
    }
}

