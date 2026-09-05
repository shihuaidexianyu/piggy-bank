package com.shihuaidexianyu.money.ui.accounts

import com.shihuaidexianyu.money.domain.model.AccountKind

enum class AccountOrderGroup { FUNDING, INVESTMENT, HIDDEN }

val ReorderAccountItemUiModel.orderGroup: AccountOrderGroup
    get() = when {
        isHidden -> AccountOrderGroup.HIDDEN
        kind == AccountKind.INVESTMENT -> AccountOrderGroup.INVESTMENT
        else -> AccountOrderGroup.FUNDING
    }

/** Keep other groups in their existing storage slots, including hidden accounts. */
fun moveAccountWithinGroup(
    accounts: List<ReorderAccountItemUiModel>,
    accountId: Long,
    targetId: Long,
): List<ReorderAccountItemUiModel> {
    val account = accounts.find { it.id == accountId } ?: return accounts
    val target = accounts.find { it.id == targetId } ?: return accounts
    if (accountId == targetId || account.orderGroup != target.orderGroup) return accounts
    val group = accounts.filter { it.orderGroup == account.orderGroup }.toMutableList()
    val from = group.indexOfFirst { it.id == accountId }
    val to = group.indexOfFirst { it.id == targetId }
    group.add(to, group.removeAt(from))
    val reordered = group.iterator()
    return accounts.map { if (it.orderGroup == account.orderGroup) reordered.next() else it }
}

fun sortAccountGroups(
    accounts: List<ReorderAccountItemUiModel>,
    comparator: Comparator<ReorderAccountItemUiModel>,
): List<ReorderAccountItemUiModel> {
    val groups = accounts.groupBy { it.orderGroup }
        .mapValues { (_, items) -> items.sortedWith(comparator).iterator() }
    return accounts.map { groups.getValue(it.orderGroup).next() }
}
