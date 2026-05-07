package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.shihuaidexianyu.money.domain.model.AccountGroupType

data class AccountOptionUiModel(
    val id: Long,
    val name: String,
    val groupType: AccountGroupType,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerDialog(
    title: String,
    accounts: List<AccountOptionUiModel>,
    selectedAccountId: Long? = null,
    disabledAccountIds: Set<Long> = emptySet(),
    noSelectionLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onClearSelection: (() -> Unit)? = null,
) {
    val groupedAccounts = AccountGroupType.entries.mapNotNull { groupType ->
        accounts.filter { it.groupType == groupType }
            .takeIf { it.isNotEmpty() }
            ?.let { groupType to it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "按账户分类分组展示，组内为具体账户",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

            groupedAccounts.forEach { (groupType, groupAccounts) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = groupType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyListSection {
                            groupAccounts.forEachIndexed { index, account ->
                                val isDisabled = account.id in disabledAccountIds
                                MoneyListRow(
                                    title = account.name,
                                    subtitle = if (isDisabled) "当前场景不可选" else null,
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
                                if (index != groupAccounts.lastIndex) {
                                    MoneySectionDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

