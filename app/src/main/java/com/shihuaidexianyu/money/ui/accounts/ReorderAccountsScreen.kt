package com.shihuaidexianyu.money.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.ui.common.AsyncContentRenderer
import com.shihuaidexianyu.money.ui.common.CollectUiEffects
import com.shihuaidexianyu.money.ui.common.LocalCurrencySymbol
import com.shihuaidexianyu.money.ui.common.MoneyDimens
import com.shihuaidexianyu.money.ui.common.MoneyEmptyStateCard
import com.shihuaidexianyu.money.ui.common.MoneyFormPage
import com.shihuaidexianyu.money.ui.common.formatInAppAmount
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import com.shihuaidexianyu.money.ui.common.rememberDirtyFormBackAction

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
    ReorderAccountsContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onMove = viewModel::moveAccount,
        onMoveUp = viewModel::moveAccountUp,
        onMoveDown = viewModel::moveAccountDown,
        onSortByBalance = viewModel::sortByBalance,
        onSortByRecent = viewModel::sortByRecentUse,
        onSortByName = viewModel::sortByName,
        onUndoChanges = viewModel::undoChanges,
        onRetry = viewModel::retryLoad,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ReorderAccountsContent(
    state: ReorderAccountsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMove: (Long, Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onSortByBalance: () -> Unit,
    onSortByRecent: () -> Unit,
    onSortByName: () -> Unit,
    onUndoChanges: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val guardedBack = rememberDirtyFormBackAction(state.isDirty, onBack)
    BackHandler(enabled = state.isSaving) { }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val dragState = rememberAccountOrderDragState(listState, state.accounts, onMove) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    var showMenu by remember { mutableStateOf(false) }
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }
    val groups = remember(state.accounts) { state.accounts.groupBy { it.orderGroup } }
    val canEdit = !state.isLoading && state.loadErrorMessageRes == null && !state.isSaving
    val canUseActions = canEdit && dragState.draggedId == null && dragState.settlingId == null
    val hasSortableGroup = groups.values.any { it.size > 1 }
    val settings = PortableSettings(currencySymbol = LocalCurrencySymbol.current)
    val savingLabel = stringResource(R.string.accounts_order_saving)

    MoneyFormPage(
        title = stringResource(R.string.accounts_order),
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onBack = { if (!state.isSaving) guardedBack() },
        listState = listState,
        contentPadding = PaddingValues(horizontal = MoneyDimens.screenHorizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.Top,
        trailing = {
            Box {
                IconButton(onClick = { showMenu = true }, enabled = canUseActions && hasSortableGroup) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.accounts_order_more))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    listOf(
                        R.string.accounts_sort_balance to onSortByBalance,
                        R.string.accounts_sort_recent to onSortByRecent,
                        R.string.accounts_sort_name to onSortByName,
                    ).forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = { showMenu = false; action() },
                            enabled = canUseActions,
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_restore_order)) },
                        onClick = { showMenu = false; onUndoChanges() },
                        enabled = canUseActions && state.isDirty,
                    )
                }
            }
            TextButton(onClick = onSave, enabled = canUseActions && state.isDirty) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).semantics { contentDescription = savingLabel },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
    ) {
        if (state.isLoading || state.loadErrorMessageRes != null) {
            item(key = "loading") {
                AsyncContentRenderer(
                    content = formAsyncContent(state, state.isLoading, state.loadErrorMessageRes?.let { stringResource(it) }, "reorder-accounts"),
                    onRetry = onRetry,
                    modifier = Modifier.heightIn(min = 240.dp),
                    data = { _, _ -> },
                )
            }
            return@MoneyFormPage
        }
        if (state.accounts.isEmpty()) {
            item(key = "empty") {
                MoneyEmptyStateCard(
                    title = stringResource(R.string.accounts_none),
                    subtitle = stringResource(R.string.accounts_reorder_empty_description),
                )
            }
        } else {
            item(key = "hint") {
                Text(
                    stringResource(if (hasSortableGroup) R.string.accounts_reorder_drag_hint else R.string.accounts_reorder_single_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            }
            AccountOrderGroup.entries.forEach { group ->
                val accounts = groups[group].orEmpty()
                if (accounts.isEmpty()) return@forEach
                val isHidden = group == AccountOrderGroup.HIDDEN
                item(key = "header_$group", contentType = "header") {
                    OrderGroupHeader(
                        group = group,
                        count = accounts.size,
                        expanded = hiddenExpanded,
                        enabled = canUseActions,
                        onToggle = { hiddenExpanded = !hiddenExpanded },
                    )
                }
                if (isHidden && !hiddenExpanded) return@forEach
                itemsIndexed(accounts, key = { _, account -> account.id }, contentType = { _, _ -> "account" }) { index, account ->
                    val isFloating = dragState.draggedId == account.id || dragState.settlingId == account.id
                    OrderAccountRow(
                        account = account,
                        amount = formatInAppAmount(account.balance, settings),
                        hasHandle = accounts.size > 1,
                        canMoveUp = canUseActions && index > 0,
                        canMoveDown = canUseActions && index < accounts.lastIndex,
                        onMoveUp = { onMoveUp(account.id) },
                        onMoveDown = { onMoveDown(account.id) },
                        showDivider = index != accounts.lastIndex,
                        isFloating = isFloating,
                        modifier = Modifier
                            .then(if (isFloating) Modifier else Modifier.animateItem())
                            .zIndex(if (isFloating) 1f else 0f)
                            .graphicsLayer { translationY = dragState.translation(account.id) },
                        handleModifier = Modifier.pointerInput(account.id, canEdit) {
                            if (canEdit) detectDragGestures(
                                onDragStart = {
                                    dragState.start(account.id)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, delta -> change.consume(); dragState.drag(delta.y) },
                                onDragEnd = { dragState.finish() },
                                onDragCancel = { dragState.finish() },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderGroupHeader(
    group: AccountOrderGroup,
    count: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val hidden = group == AccountOrderGroup.HIDDEN
    val stateLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (hidden) Modifier.clickable(enabled = enabled, onClick = onToggle) else Modifier)
            .semantics { heading(); if (hidden) stateDescription = stateLabel }
            .padding(top = 12.dp, bottom = 8.dp)
            .heightIn(min = if (hidden) 48.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(when (group) {
                AccountOrderGroup.FUNDING -> R.string.account_kind_funding
                AccountOrderGroup.INVESTMENT -> R.string.account_kind_investment
                AccountOrderGroup.HIDDEN -> R.string.accounts_hidden_short
            }),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (hidden) {
            Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OrderAccountRow(
    account: ReorderAccountItemUiModel,
    amount: String,
    hasHandle: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    showDivider: Boolean,
    isFloating: Boolean,
    modifier: Modifier,
    handleModifier: Modifier,
) {
    val up = stringResource(R.string.action_move_up)
    val down = stringResource(R.string.action_move_down)
    Column(
        modifier = modifier.testTag("account_order_row_${account.id}")
            .graphicsLayer {
                shadowElevation = if (isFloating) 4.dp.toPx() else 0f
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            }
            .background(if (isFloating) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(up) { onMoveUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction(down) { onMoveDown(); true })
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasHandle) {
                Box(
                    modifier = handleModifier.size(48.dp).testTag("account_order_handle_${account.id}").clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isFloating) 0f else 0.5f))
    }
}
